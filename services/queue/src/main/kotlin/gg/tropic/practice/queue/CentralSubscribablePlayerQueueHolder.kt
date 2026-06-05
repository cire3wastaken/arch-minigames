package gg.tropic.practice.queue

import gg.tropic.practice.games.livePlayerCount
import gg.tropic.practice.games.manager.GameManager
import gg.tropic.practice.metadata.Metadata
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.region.Region
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import lol.arch.symphony.api.Symphony
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * @author Subham
 * @since 6/15/25
 */
class CentralSubscribablePlayerQueueHolder : Runnable
{
    val playerQueues = ConcurrentHashMap<String, SubscribablePlayerQueue>()

    // Observability metrics
    private val processingCycleCount = AtomicLong(0)
    private val totalProcessingTimeMs = AtomicLong(0)
    private val lastProcessingDurationMs = AtomicLong(0)
    private val slowProcessingThresholdMs = 100L
    private var lastHealthLogTime = 0L

    fun trackPlayerQueue(playerQueue: SubscribablePlayerQueue)
    {
        playerQueues[playerQueue.id] = playerQueue
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "queue.lifecycle"
            message = "Registered queue: ${playerQueue.id}"
            level = SentryLevel.INFO
        })
    }

    fun isHolding(queueId: String) = playerQueues.containsKey(queueId)

    fun forgetPlayerQueue(id: String)
    {
        playerQueues.remove(id)
    }

    fun subscribe(queueID: String, entry: QueueEntry)
    {
        val existingQueue = queueOfPlayer(entry.leader)
        if (existingQueue != null)
        {
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "queue.subscribe"
                message = "Player ${entry.leader} already in queue ${existingQueue.first.id}, unsubscribing first"
                level = SentryLevel.WARNING
            })
            existingQueue.first.unsubscribe(entry)
        }

        val targetQueue = playerQueues[queueID]
        if (targetQueue != null) {
            targetQueue.subscribe(entry)
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "queue.subscribe"
                message = "Subscribed ${entry.players.size} players to $queueID (leader: ${entry.leader})"
                level = SentryLevel.INFO
                setData("queue_id", queueID)
                setData("party_size", entry.players.size)
                setData("region", entry.preferredQueueRegion.name)
            })
        } else {
            Sentry.captureMessage("Attempted to subscribe to non-existent queue: $queueID") { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("leader", entry.leader.toString())
                scope.setExtra("queue_id", queueID)
            }
        }
    }

    fun unsubscribe(player: UUID)
    {
        queueOfPlayer(player)?.apply {
            first.unsubscribe(second.data)
        }
    }

    fun onProcessAll()
    {
        val cycleStart = System.currentTimeMillis()
        val cycleNumber = processingCycleCount.incrementAndGet()

        var queuesProcessed = 0
        var totalPlayersInQueue = 0

        playerQueues.values.forEach { queue ->
            val queueSize = queue.internalQueue.size()
            totalPlayersInQueue += queueSize

            val queueStart = System.currentTimeMillis()
            queue.processAndDestroy()
            val queueDuration = System.currentTimeMillis() - queueStart

            // Log slow individual queue processing
            if (queueDuration > slowProcessingThresholdMs) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "queue.slow_processing"
                    message = "Slow queue processing: ${queue.id} took ${queueDuration}ms"
                    level = SentryLevel.WARNING
                    setData("queue_id", queue.id)
                    setData("duration_ms", queueDuration)
                    setData("queue_size", queueSize)
                })
            }

            queuesProcessed++
        }

        val cycleDuration = System.currentTimeMillis() - cycleStart
        lastProcessingDurationMs.set(cycleDuration)
        totalProcessingTimeMs.addAndGet(cycleDuration)

        // Alert on very slow processing cycles
        if (cycleDuration > slowProcessingThresholdMs * 3) {
            Sentry.captureMessage("Queue processing cycle exceeded threshold: ${cycleDuration}ms") { scope ->
                scope.level = SentryLevel.WARNING
                scope.setExtra("cycle_number", cycleNumber.toString())
                scope.setExtra("duration_ms", cycleDuration.toString())
                scope.setExtra("queues_processed", queuesProcessed.toString())
                scope.setExtra("total_players_queued", totalPlayersInQueue.toString())
            }
        }

        // Periodic health logging every 30 seconds
        val now = System.currentTimeMillis()
        if (now - lastHealthLogTime > 30_000L) {
            lastHealthLogTime = now
            val avgProcessingTime = if (cycleNumber > 0) totalProcessingTimeMs.get() / cycleNumber else 0
            Sentry.addBreadcrumb(Breadcrumb().apply {
                category = "queue.health"
                message = "Queue health: ${playerQueues.size} queues, $totalPlayersInQueue players, avg cycle: ${avgProcessingTime}ms"
                level = SentryLevel.INFO
                setData("queue_count", playerQueues.size)
                setData("total_players", totalPlayersInQueue)
                setData("avg_cycle_ms", avgProcessingTime)
                setData("last_cycle_ms", cycleDuration)
                setData("total_cycles", cycleNumber)
            })
        }
    }

    fun queueOfPlayer(player: UUID) = playerQueues.values
        .firstNotNullOfOrNull { queue ->
            queue.internalQueue.listElements()
                .firstOrNull { entry -> player in entry.data.players }
                ?.let { entry ->
                    queue to entry
                }
        }

    fun configure(executor: ScheduledExecutorService)
    {
        thread(
            start = true,
            block = ::run,
            isDaemon = true
        )

        executor.scheduleAtFixedRate(::onExpansionUpdate, 0L, 1000L, TimeUnit.MILLISECONDS)
        executor.scheduleAtFixedRate(::onMetadataUpdate, 0L, 500L, TimeUnit.MILLISECONDS)

        Symphony
            .createEventSubscriber()
            .onLogout { player ->
                val presentInAnyQueue = queueOfPlayer(player)
                presentInAnyQueue?.apply {
                    RedisShared.sendMessage(
                        second.data.players,
                        listOf(
                            "&c&lYou were removed from the queue as a player in your group disconnected from the server!"
                        )
                    )

                    first.unsubscribe(second.data)
                }
            }
            .subscribe()
            .join()
    }

    fun onMetadataUpdate()
    {
        val games = GameManager.allGames()
        playerQueues.values.forEach { queue ->
            val gamesMatchingQueueID = games.filter { it.queueId == queue.id }
            val playersInGame = gamesMatchingQueueID.sumOf { it.livePlayerCount() }

            Metadata.writer().write(
                "queue:users-queued:${queue.id}",
                queue.internalQueue.size().toString()
            )

            Metadata.writer().write(
                "queue:users-playing:${queue.id}",
                playersInGame.toString()
            )
        }
    }

    fun onExpansionUpdate()
    {
        playerQueues.values.forEach { queue ->
            queue.internalQueue.listElements().forEach { queueEntry ->
                val entry = queueEntry.data
                var requiresUpdates = false

                val rangeExpansionUpdates = { time: Long ->
                    System.currentTimeMillis() >= time + 1500L
                }

                if (queue.toQueueIDComponents()?.queueType == QueueType.Ranked)
                {
                    if (rangeExpansionUpdates(entry.lastELORangeExpansion))
                    {
                        entry.leaderRangedELO.diffsBy = min(
                            2_000, // TODO is 2000 going to be the max ELO?
                            (entry.leaderRangedELO.diffsBy * 1.5).toInt()
                        )
                        entry.lastELORangeExpansion = System.currentTimeMillis()
                        requiresUpdates = true
                    }
                }

                val previousPingDiff = entry.leaderRangedPing.diffsBy
                if (entry.maxPingDiff != -1)
                {
                    if (entry.leaderRangedPing.diffsBy < entry.maxPingDiff)
                    {
                        if (rangeExpansionUpdates(entry.lastPingRangeExpansion))
                        {
                            entry.leaderRangedPing.diffsBy = min(
                                entry.maxPingDiff,
                                (entry.leaderRangedPing.diffsBy * 1.5).toInt()
                            )
                            entry.lastPingRangeExpansion = System.currentTimeMillis()
                            val differential = entry.leaderRangedPing.diffsBy - previousPingDiff
                            entry.lastRecordedDifferential = differential

                            requiresUpdates = true

                            val pingRange = entry.leaderRangedPing.toIntRangeInclusive()
                            RedisShared.sendMessage(
                                entry.players,
                                listOf(
                                    "{secondary}You are matchmaking in an ping range of ${
                                        "&a[${max(0, pingRange.first)} -> ${pingRange.last}]{secondary}"
                                    } &7(expanded by ±${
                                        differential
                                    }). ${
                                        if (entry.leaderRangedPing.diffsBy == entry.maxPingDiff) "&lThe range will no longer be expanded as it has reached its maximum of ±${entry.maxPingDiff}!" else ""
                                    }"
                                )
                            )
                        }
                    }
                }

                if (
                    entry.preferredQueueRegion != Region.Both &&
                    System.currentTimeMillis() - entry.joinQueueTimestamp >= 10_000L
                )
                {
                    requiresUpdates = true

                    val previousRegion = entry.preferredQueueRegion
                    entry.preferredQueueRegion = Region.Both

                    RedisShared.sendMessage(
                        entry.players,
                        listOf(
                            "{secondary}You are now matchmaking in the &aGlobal{secondary} queue as we could not find an opponent for you in the {primary}${previousRegion.name}{secondary} queue."
                        )
                    )
                }

                if (requiresUpdates)
                {
                    queue.updateStates(entry)
                }
            }
        }
    }

    override fun run()
    {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "queue.lifecycle"
            message = "Queue processing loop started"
            level = SentryLevel.INFO
        })

        var lastLoopEndTime = System.currentTimeMillis()
        var loopIterationCount = 0L
        var maxLoopGapMs = 0L
        var totalLoopGapMs = 0L
        var lastMetricsEmitTime = System.currentTimeMillis()
        val metricsEmitIntervalMs = 10_000L // Emit metrics every 10 seconds
        val loopGapAlertThresholdMs = 100L // Alert if gap between loops exceeds this

        while (true)
        {
            val loopStartTime = System.currentTimeMillis()
            val loopGap = loopStartTime - lastLoopEndTime
            loopIterationCount++

            // Track loop gaps (time between end of last loop and start of this one)
            if (loopGap > loopGapAlertThresholdMs) {
                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "queue.loop_gap"
                    message = "Large loop gap detected: ${loopGap}ms (iteration $loopIterationCount)"
                    level = SentryLevel.WARNING
                    setData("gap_ms", loopGap)
                    setData("iteration", loopIterationCount)
                    setData("expected_gap_ms", 50)
                })

                // Alert on very large gaps (likely indicates server hang)
                if (loopGap > 500L) {
                    Sentry.captureMessage("Queue processing loop stalled for ${loopGap}ms") { scope ->
                        scope.level = SentryLevel.ERROR
                        scope.setExtra("gap_ms", loopGap.toString())
                        scope.setExtra("iteration", loopIterationCount.toString())
                        scope.setExtra("total_queues", playerQueues.size.toString())
                        scope.setTag("alert_type", "loop_stall")
                    }
                }
            }

            totalLoopGapMs += loopGap
            if (loopGap > maxLoopGapMs) {
                maxLoopGapMs = loopGap
            }

            // Run the actual processing with transaction tracking
            val transaction = Sentry.startTransaction("queue.process_cycle", "queue.loop")
            transaction.setData("iteration", loopIterationCount)
            transaction.setData("queues_count", playerQueues.size)

            runCatching {
                onProcessAll()
                transaction.status = SpanStatus.OK
            }.onFailure { throwable ->
                transaction.throwable = throwable
                transaction.status = SpanStatus.INTERNAL_ERROR
                Sentry.captureException(throwable) { scope ->
                    scope.setExtra("processing_cycle", processingCycleCount.get().toString())
                    scope.setExtra("total_queues", playerQueues.size.toString())
                    scope.setExtra("last_processing_duration_ms", lastProcessingDurationMs.get().toString())
                }
                throwable.printStackTrace()
            }

            val processingDuration = System.currentTimeMillis() - loopStartTime
            transaction.setMeasurement("processing_duration_ms", processingDuration.toDouble())
            transaction.setMeasurement("loop_gap_ms", loopGap.toDouble())
            transaction.finish()

            // Emit aggregated metrics every 10 seconds
            val now = System.currentTimeMillis()
            if (now - lastMetricsEmitTime >= metricsEmitIntervalMs) {
                val avgLoopGap = if (loopIterationCount > 0) totalLoopGapMs.toDouble() / loopIterationCount else 0.0
                val loopsPerSecond = loopIterationCount.toDouble() / ((now - lastMetricsEmitTime) / 1000.0)

                // Create a metrics transaction for dashboard visibility
                val metricsTransaction = Sentry.startTransaction("queue.metrics", "queue.health")
                metricsTransaction.setMeasurement("loops_per_second", loopsPerSecond)
                metricsTransaction.setMeasurement("avg_loop_gap_ms", avgLoopGap)
                metricsTransaction.setMeasurement("max_loop_gap_ms", maxLoopGapMs.toDouble())
                metricsTransaction.setMeasurement("total_iterations", loopIterationCount.toDouble())
                metricsTransaction.setMeasurement("active_queues", playerQueues.size.toDouble())
                metricsTransaction.setData("avg_processing_time_ms", if (processingCycleCount.get() > 0) totalProcessingTimeMs.get() / processingCycleCount.get() else 0)
                metricsTransaction.status = SpanStatus.OK
                metricsTransaction.finish()

                Sentry.addBreadcrumb(Breadcrumb().apply {
                    category = "queue.metrics"
                    message = "Loop metrics: ${String.format("%.1f", loopsPerSecond)} loops/sec, avg gap: ${String.format("%.1f", avgLoopGap)}ms, max gap: ${maxLoopGapMs}ms"
                    level = SentryLevel.INFO
                    setData("loops_per_second", loopsPerSecond)
                    setData("avg_loop_gap_ms", avgLoopGap)
                    setData("max_loop_gap_ms", maxLoopGapMs)
                    setData("total_iterations", loopIterationCount)
                })

                // Reset metrics for next interval
                lastMetricsEmitTime = now
                loopIterationCount = 0
                maxLoopGapMs = 0
                totalLoopGapMs = 0
            }

            lastLoopEndTime = System.currentTimeMillis()
            Thread.sleep(50L)
        }
    }
}
