package gg.tropic.practice.queue

import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.application.api.defaults.map.MapDataSync
import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.manager.GameManager
import gg.tropic.practice.games.matchmaking.JoinIntoGameRequest
import gg.tropic.practice.games.matchmaking.JoinIntoGameResult
import gg.tropic.practice.games.matchmaking.JoinIntoGameStatus
import gg.tropic.practice.games.matchmaking.MatchmakingMetadata
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.minigame.MiniGameConfiguration
import gg.tropic.practice.minigame.MiniGameMode
import gg.tropic.practice.minigame.MiniGameRPC
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.region.Region
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Subham
 * @since 6/15/25
 */
abstract class AbstractSubscribableMinigamePlayerQueue(
    private val miniGameMode: MiniGameMode,
    private val kit: ImmutableKit,
    private val queueType: QueueType,
    private val selectNewestInstance: Boolean = false,
    private val teamSize: Int = miniGameMode.teamSize,
    private val miniProvider: MiniProviderVersion = MiniProviderVersion.LEGACY,
    override val id: String = queueId {
        kit(kit.id)
        queueType(queueType)
        teamSize(teamSize)
    },
    override val internalQueue: InternalQueue<QueueEntry> = InternalQueue()
) : SubscribablePlayerQueue
{
    companion object {
        // Track RPC failures per server instance
        private val instanceFailureCounts = ConcurrentHashMap<String, AtomicInteger>()
        // Exclude an instance from join selection after even a single timeout (fast breaker)
        // so we stop funneling players into an unresponsive instance immediately...
        private const val FAILURE_THRESHOLD_EXCLUDE = 1
        // ...but only request an actual restart once it crosses the higher threshold.
        private const val FAILURE_THRESHOLD_RESTART = 3
        private const val FAILURE_RESET_INTERVAL_MS = 60_000L // Reset failures after 1 minute
        private val lastFailureReset = ConcurrentHashMap<String, Long>()

        // Track which instances we've already sent restart requests to
        private val restartRequestsSent = ConcurrentHashMap<String, Long>()

        private val inFlightJoins = ConcurrentHashMap<String, AtomicInteger>()
        private const val MAX_IN_FLIGHT_PER_INSTANCE = 2

        // Transient join failures (a stale GameManager entry pointing at a game that has
        // already emptied out, or a busy team lock) shouldn't spawn a fallback game right
        // away — that's what produces a storm of doomed games. Instead, back the party off
        // briefly and re-queue: by the next pass the stale listing has expired from
        // GameManager's 2s cache, so they cleanly join a real game or create one.
        private val joinRetryCounts = ConcurrentHashMap<UUID, Int>()
        private val joinRetryBackoffUntil = ConcurrentHashMap<UUID, Long>()
        private const val MAX_TRANSIENT_JOIN_RETRIES = 5
        private const val TRANSIENT_JOIN_BACKOFF_MS = 2_500L

        fun isJoinRetryBackingOff(leader: UUID): Boolean =
            System.currentTimeMillis() < (joinRetryBackoffUntil[leader] ?: 0L)

        fun clearJoinRetryState(leader: UUID) {
            joinRetryCounts.remove(leader)
            joinRetryBackoffUntil.remove(leader)
        }

        fun shouldBackOffAndRetry(leader: UUID): Boolean {
            val attempts = joinRetryCounts.merge(leader, 1, Int::plus) ?: 1
            if (attempts > MAX_TRANSIENT_JOIN_RETRIES) {
                clearJoinRetryState(leader)
                return false
            }
            joinRetryBackoffUntil[leader] = System.currentTimeMillis() + TRANSIENT_JOIN_BACKOFF_MS
            return true
        }

        fun incrementInFlight(serverId: String): Int =
            inFlightJoins.computeIfAbsent(serverId) { AtomicInteger(0) }.incrementAndGet()

        fun decrementInFlight(serverId: String) {
            inFlightJoins[serverId]?.let { counter ->
                if (counter.decrementAndGet() < 0) counter.set(0)
            }
        }

        fun getInFlight(serverId: String): Int = inFlightJoins[serverId]?.get() ?: 0

        /**
         * True when an instance already has the max number of un-acked joins riding on it.
         * Such instances are skipped so we don't keep hammering one that isn't replying.
         */
        fun isInstanceSaturated(serverId: String): Boolean =
            getInFlight(serverId) >= MAX_IN_FLIGHT_PER_INSTANCE

        fun recordInstanceFailure(serverId: String) {
            val now = System.currentTimeMillis()
            val lastReset = lastFailureReset[serverId] ?: 0L

            // Reset counter if it's been more than a minute
            if (now - lastReset > FAILURE_RESET_INTERVAL_MS) {
                instanceFailureCounts[serverId] = AtomicInteger(0)
                lastFailureReset[serverId] = now
                restartRequestsSent.remove(serverId)
            }

            val failures = instanceFailureCounts.computeIfAbsent(serverId) { AtomicInteger(0) }
            val count = failures.incrementAndGet()

            if (count >= FAILURE_THRESHOLD_RESTART) {
                io.sentry.Sentry.captureMessage("Instance $serverId exceeded failure threshold ($count failures)") { scope ->
                    scope.level = io.sentry.SentryLevel.ERROR
                    scope.setTag("alert_type", "instance_failure")
                    scope.setExtra("server_id", serverId)
                    scope.setExtra("failure_count", count.toString())
                }

                // Only send restart request once per failure window
                if (!restartRequestsSent.containsKey(serverId)) {
                    triggerInstanceRestart(serverId, count)
                    restartRequestsSent[serverId] = now
                }
            }
        }

        /**
         * Sends RPC to failing instance requesting it to restart
         */
        private fun triggerInstanceRestart(serverId: String, failureCount: Int) {
            io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "queue.instance_restart"
                message = "Triggering restart for failing instance: $serverId"
                level = io.sentry.SentryLevel.WARNING
                setData("server_id", serverId)
                setData("failure_count", failureCount)
            })

            MiniGameRPC.restartInstanceService
                .call(
                    gg.tropic.practice.games.restart.RestartInstanceRequest(
                        targetServer = serverId,
                        delaySeconds = 60,
                        reason = "RPC failure threshold exceeded ($failureCount failures)"
                    )
                )
                .thenAccept { response ->
                    io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                        category = "queue.instance_restart"
                        message = "Restart response from $serverId: ${response.status}"
                        level = if (response.status == gg.tropic.practice.games.restart.RestartStatus.SUCCESS)
                            io.sentry.SentryLevel.INFO else io.sentry.SentryLevel.WARNING
                        setData("status", response.status.name)
                        setData("message", response.message ?: "")
                    })
                }
                .exceptionally { ex ->
                    io.sentry.Sentry.captureException(ex) { scope ->
                        scope.setTag("rpc_service", "restartInstanceService")
                        scope.setExtra("server_id", serverId)
                    }
                    null
                }
        }

        fun isInstanceFailing(serverId: String): Boolean {
            val now = System.currentTimeMillis()
            val lastReset = lastFailureReset[serverId] ?: 0L

            // If it's been more than a minute, give the instance another chance
            if (now - lastReset > FAILURE_RESET_INTERVAL_MS) {
                return false
            }

            val failures = instanceFailureCounts[serverId]?.get() ?: 0
            return failures >= FAILURE_THRESHOLD_EXCLUDE
        }

        fun getInstanceFailureCount(serverId: String): Int {
            return instanceFailureCounts[serverId]?.get() ?: 0
        }

        /**
         * Returns all instances currently exceeding the failure threshold
         */
        fun getFailingInstances(): Set<String> {
            val now = System.currentTimeMillis()
            return instanceFailureCounts.entries
                .filter { (serverId, count) ->
                    val lastReset = lastFailureReset[serverId] ?: 0L
                    // Only include if not expired and exceeds threshold
                    (now - lastReset <= FAILURE_RESET_INTERVAL_MS) && count.get() >= FAILURE_THRESHOLD_EXCLUDE
                }
                .map { it.key }
                .toSet()
        }
    }

    abstract fun constructConfigurationForInitiatorEntry(entry: QueueEntry): MiniGameConfiguration

    protected open val createsFallbackGameOnJoinFailure: Boolean = true

    override fun onProcess(): List<QueueEntry>
    {
        if (internalQueue.isEmpty())
        {
            return emptyList()
        }

        val targetEntry = playersInQueue().first()

        if (isJoinRetryBackingOff(targetEntry.data.leader))
        {
            return emptyList()
        }

        val preferredRegion = if (targetEntry.data.preferredQueueRegion == Region.Both)
            Region.NA else targetEntry.data.preferredQueueRegion

        val alreadyInGame = GameManager.allGames().any { gameRef ->
            gameRef.state != GameState.Completed &&
                targetEntry.data.players.any { it in gameRef.onlinePlayerIds.orEmpty() }
        }

        if (alreadyInGame)
        {
            RedisShared.sendMessage(
                targetEntry.data.players,
                listOf(
                    "&cYou were removed from the queue as you are already in a game!"
                )
            )

            return listOf(targetEntry.data)
        }

        // Private games always create new instances - skip joining existing games
        val isPrivateGame = targetEntry.data.miniGameQueueConfiguration?.isPrivateGame == true

        // Get current failing instances to exclude from existing game selection
        val failingInstances = getFailingInstances()

        val existingGameRequiringPlayers = if (isPrivateGame) null else GameManager.allGames()
            .filter {
                // FIRST: Filter out games on failing instances
                if (it.server in failingInstances) {
                    return@filter false
                }

                if (isInstanceSaturated(it.server)) {
                    return@filter false
                }

                if (it.isPrivateGame) {
                    return@filter false
                }

                var conditions = it.queueId == id &&
                    (it.state == GameState.Waiting || it.state == GameState.Starting)

                if (targetEntry.data.miniGameQueueConfiguration != null)
                {
                    if (targetEntry.data.miniGameQueueConfiguration!!.requiredMapID != null)
                    {
                        // Ensure this existing game has a map
                        conditions = conditions &&
                            it.mapID == targetEntry.data.miniGameQueueConfiguration!!.requiredMapID
                    }

                    if (targetEntry.data.miniGameQueueConfiguration!!.bracket != null)
                    {
                        conditions = conditions &&
                            it.metadata?.bracket == targetEntry.data.miniGameQueueConfiguration!!.bracket
                    }

                    if (targetEntry.data.miniGameQueueConfiguration!!.excludeMiniInstance != null)
                    {
                        conditions = conditions &&
                            it.server != targetEntry.data.miniGameQueueConfiguration!!.excludeMiniInstance
                    }
                }

                return@filter conditions
            }
            .filter { it.players.size + targetEntry.data.players.size <= miniGameMode.maxPlayers() }
            .maxByOrNull { it.players.size }

        val map = targetEntry.data.miniGameQueueConfiguration?.requiredMapID
            ?.let { MapDataSync.cached().maps[it] }
            ?: MapDataSync
                .selectRandomMapCompatibleWith(kit, miniProvider)
            ?: return run {
                RedisShared.sendMessage(
                    targetEntry.data.players,
                    listOf(
                        "&cWe found no map compatible with the kit you are queueing for!"
                    )
                )

                listOf(targetEntry.data)
            }

        if (existingGameRequiringPlayers != null)
        {
            val serverId = existingGameRequiringPlayers.server

            // Skip instances that have failed too many times recently, or that already have
            // un-acked joins riding on them (don't keep hammering one that isn't replying).
            if (isInstanceFailing(serverId) || isInstanceSaturated(serverId)) {
                io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                    category = "queue.instance_skip"
                    message = "Skipping instance $serverId (${getInstanceFailureCount(serverId)} failures, ${getInFlight(serverId)} in flight)"
                    level = io.sentry.SentryLevel.WARNING
                })
                // Don't try to join, fall through to create a new game instead
            } else {
                // NON-BLOCKING: Fire the RPC and handle result async
                // Return entry immediately so it's removed from queue
                incrementInFlight(serverId)
                MiniGameRPC.joinIntoGameService
                    .call(
                        JoinIntoGameRequest(
                            server = serverId,
                            players = targetEntry.data.players.toSet(),
                            game = existingGameRequiringPlayers
                        )
                    )
                    .orTimeout(9, TimeUnit.SECONDS)
                    .thenAccept { joinGameResult ->
                        if (joinGameResult.status == JoinIntoGameStatus.SUCCESS) {
                            clearJoinRetryState(targetEntry.data.leader)
                            RedisShared.redirect(
                                targetEntry.data.players,
                                serverId
                            )
                        } else {
                            if (joinGameResult.status != JoinIntoGameStatus.FAILED_ALREADY_STARTED &&
                                joinGameResult.status != JoinIntoGameStatus.FAILED_PRIVATE_GAME &&
                                joinGameResult.status != JoinIntoGameStatus.FAILED_GAME_NOT_FOUND) {
                                recordInstanceFailure(serverId)
                            }
                            io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                                category = "queue.join_failed"
                                message = "Failed to join game on $serverId: ${joinGameResult.status}"
                                level = io.sentry.SentryLevel.WARNING
                                setData("server", serverId)
                                setData("status", joinGameResult.status.name)
                            })
                            println("Failed to join into game for ${targetEntry.data.leader} (${joinGameResult.status})")

                            val transient = joinGameResult.status == JoinIntoGameStatus.FAILED_GAME_NOT_FOUND ||
                                joinGameResult.status == JoinIntoGameStatus.FAILED_GAME_BUSY
                            handleJoinFailure(targetEntry, preferredRegion, map, joinGameResult.status.name, transient)
                        }
                    }
                    .exceptionally { ex ->
                        // RPC timeout or failure
                        recordInstanceFailure(serverId)
                        io.sentry.Sentry.captureException(ex) { scope ->
                            scope.setTag("rpc_service", "joinIntoGameService")
                            scope.setTag("alert_type", "rpc_timeout")
                            scope.setExtra("server", serverId)
                            scope.setExtra("game_id", existingGameRequiringPlayers.uniqueId.toString())
                            scope.setExtra("failure_count", getInstanceFailureCount(serverId).toString())
                        }
                        val cause = (ex as? java.util.concurrent.CompletionException)?.cause ?: ex
                        val reason = when (cause) {
                            is java.util.concurrent.TimeoutException -> "no RPC reply within deadline"
                            else -> "${cause::class.simpleName}: ${cause.message}"
                        }
                        println("RPC failed for join into game on $serverId ($reason)")

                        handleJoinFailure(targetEntry, preferredRegion, map, "RPC_FAILURE", transient = true)
                        null
                    }
                    // Release the in-flight slot once the join has settled either way.
                    .whenComplete { _, _ -> decrementInFlight(serverId) }

                // Return entry immediately - it's being handled async
                return listOf(targetEntry.data)
            }
        }

        val playersToTake = targetEntry.data.players.toMutableSet()
        val teams = TeamIdentifier.ID.values
            .take(miniGameMode.teamCount)
            .mapIndexed { index, identifier ->
                if (playersToTake.isEmpty())
                {
                    return@mapIndexed GameTeam(identifier, mutableSetOf())
                }

                val amount = playersToTake.take(teamSize)
                playersToTake.removeAll(amount)

                return@mapIndexed GameTeam(identifier, amount.toMutableSet())
            }

        if (playersToTake.isNotEmpty())
        {
            RedisShared.sendMessage(
                targetEntry.data.players,
                listOf(
                    "&cWe were unable to fit your party of players into a game!"
                )
            )

            return listOf(targetEntry.data)
        }

        val expectation = GameExpectation(
            identifier = UUID.randomUUID(),
            players = targetEntry.data.players.toMutableSet(),
            teams = teams.toSet(),
            kitId = kit.id,
            mapId = map.name,
            queueType = queueType,
            queueId = id,
            matchmakingMetadataAPIV2 = MatchmakingMetadata(
                region = Region.NA,
                bracket = targetEntry.data.miniGameQueueConfiguration?.bracket
            ),
            miniGameConfiguration = constructConfigurationForInitiatorEntry(targetEntry.data),
            isPrivateGame = isPrivateGame,
            privateGameSettings = targetEntry.data.miniGameQueueConfiguration?.privateGameSettings
        )

        // Log minigame match found
        io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
            category = "queue.minigame_match"
            message = "Minigame match created: ${miniGameMode.javaClass.name} with ${targetEntry.data.players.size} players"
            level = io.sentry.SentryLevel.INFO
            setData("minigame_mode", miniGameMode.javaClass.name)
            setData("kit_id", kit.id)
            setData("player_count", targetEntry.data.players.size)
            setData("is_private", isPrivateGame)
            setData("game_id", expectation.identifier.toString())
        })

        GameQueueManager
            .prepareGameFor(
                map = map,
                expectation = expectation,
                // prefer NA servers if queuing globally
                region = preferredRegion,
                excludeInstance = targetEntry.data.miniGameQueueConfiguration?.excludeMiniInstance,
                blacklistedInstances = getFailingInstances(),
                selectNewestInstance = selectNewestInstance,
                version = miniProvider
            )
            .exceptionally {
                io.sentry.Sentry.captureException(it) { scope ->
                    scope.setExtra("game_id", expectation.identifier.toString())
                    scope.setExtra("minigame_mode", miniGameMode.javaClass.name)
                    scope.setExtra("player_count", targetEntry.data.players.size.toString())
                }
                it.printStackTrace()
                return@exceptionally null
            }

        return listOf(targetEntry.data)
    }

    private fun handleJoinFailure(
        targetEntry: InternalQueueEntry<QueueEntry>,
        preferredRegion: Region,
        map: gg.tropic.practice.application.api.defaults.map.ImmutableMap,
        reason: String,
        transient: Boolean = false
    ) {
        // Transient failures (stale game listing, busy lock, no RPC reply) shouldn't spawn
        // a fallback game on the spot — that's what produces a storm of doomed games when
        // an instance is flapping. Back the party off and re-queue them for another pass.
        if (transient && shouldBackOffAndRetry(targetEntry.data.leader)) {
            if (!isQueued(targetEntry.data.leader)) {
                subscribe(targetEntry.data)
            }
            io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
                category = "queue.join_retry"
                message = "Re-queued ${targetEntry.data.leader} after transient join failure ($reason)"
                level = io.sentry.SentryLevel.INFO
            })
            return
        }

        // Retries exhausted (or a hard failure): clear state and fall through to the
        // normal recovery path below.
        clearJoinRetryState(targetEntry.data.leader)

        if (createsFallbackGameOnJoinFailure) {
            handleFailedJoinWithNewGame(targetEntry, preferredRegion, map)
            return
        }

        // Singleton queues (e.g. events): never spawn a parallel game on failure.
        RedisShared.sendMessage(
            targetEntry.data.players,
            listOf("&cWe couldn't add you to the current game. Please try again in a moment. ($reason)")
        )
    }

    /**
     * Creates a new game for players when joining an existing game fails.
     * Called from async RPC failure handlers.
     */
    private fun handleFailedJoinWithNewGame(
        targetEntry: InternalQueueEntry<QueueEntry>,
        preferredRegion: Region,
        map: gg.tropic.practice.application.api.defaults.map.ImmutableMap
    ) {
        val playersToTake = targetEntry.data.players.toMutableSet()
        val teams = TeamIdentifier.ID.values
            .take(miniGameMode.teamCount)
            .mapIndexed { _, identifier ->
                if (playersToTake.isEmpty()) {
                    return@mapIndexed GameTeam(identifier, mutableSetOf())
                }
                val amount = playersToTake.take(teamSize)
                playersToTake.removeAll(amount)
                return@mapIndexed GameTeam(identifier, amount.toMutableSet())
            }

        if (playersToTake.isNotEmpty()) {
            RedisShared.sendMessage(
                targetEntry.data.players,
                listOf("&cWe were unable to fit your party of players into a game!")
            )
            return
        }

        val isPrivateGame = targetEntry.data.miniGameQueueConfiguration?.isPrivateGame == true
        val expectation = GameExpectation(
            identifier = UUID.randomUUID(),
            players = targetEntry.data.players.toMutableSet(),
            teams = teams.toSet(),
            kitId = kit.id,
            mapId = map.name,
            queueType = queueType,
            queueId = id,
            matchmakingMetadataAPIV2 = MatchmakingMetadata(
                region = Region.NA,
                bracket = targetEntry.data.miniGameQueueConfiguration?.bracket
            ),
            miniGameConfiguration = constructConfigurationForInitiatorEntry(targetEntry.data),
            isPrivateGame = isPrivateGame,
            privateGameSettings = targetEntry.data.miniGameQueueConfiguration?.privateGameSettings
        )

        io.sentry.Sentry.addBreadcrumb(io.sentry.Breadcrumb().apply {
            category = "queue.fallback_game"
            message = "Creating fallback game after failed join: ${targetEntry.data.players.size} players"
            level = io.sentry.SentryLevel.INFO
        })

        GameQueueManager
            .prepareGameFor(
                map = map,
                expectation = expectation,
                region = preferredRegion,
                excludeInstance = targetEntry.data.miniGameQueueConfiguration?.excludeMiniInstance,
                blacklistedInstances = getFailingInstances(),
                selectNewestInstance = selectNewestInstance,
                version = miniProvider
            )
            .exceptionally {
                io.sentry.Sentry.captureException(it) { scope ->
                    scope.setExtra("game_id", expectation.identifier.toString())
                    scope.setExtra("context", "fallback_game_creation")
                }
                it.printStackTrace()
                null
            }
    }
}
