package gg.tropic.practice.application

import com.mongodb.MongoClient
import com.mongodb.MongoClientURI
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.WriteModel
import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import gg.scala.aware.AwareHub
import gg.scala.aware.uri.WrappedAwareUri
import gg.scala.cache.uuid.ScalaStoreUuidCache
import gg.scala.cache.uuid.impl.distribution.DistributedRedisUuidCacheTranslator
import gg.scala.cache.uuid.resolver.impl.MojangDataResolver
import gg.scala.commons.ScalaCommons
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.consensus.Zoo
import gg.scala.store.ScalaDataStoreShared
import gg.scala.store.connection.AbstractDataStoreConnection
import gg.scala.store.connection.mongo.AbstractDataStoreMongoConnection
import gg.scala.store.connection.mongo.impl.UriDataStoreMongoConnection
import gg.scala.store.connection.mongo.impl.details.DataStoreMongoConnectionDetails
import gg.scala.store.connection.redis.AbstractDataStoreRedisConnection
import gg.tropic.practice.application.api.defaults.kit.KitDataSync
import gg.tropic.practice.application.api.defaults.kit.group.KitGroupDataSync
import gg.tropic.practice.application.api.defaults.map.MapDataSync
import gg.tropic.practice.application.platform.PracticeAPIPlatform
import gg.tropic.practice.devProvider
import gg.tropic.practice.games.manager.GameManager
import gg.tropic.practice.metadata.Metadata
import gg.tropic.practice.minigame.MiniGameSerializers
import gg.tropic.practice.namespace
import gg.tropic.practice.observability.SentryIntegration
import mc.arch.commons.communications.rpc.CommunicationGateway
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.queue.GameQueueManager
import gg.tropic.practice.replications.manager.ReplicationManager
import gg.tropic.practice.statistics.RawStatisticCRUD
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIdFrom
import gg.tropic.practice.statistics.statisticIds
import mc.arch.minigames.ugc.gateway.HostedWorldGatewayManager
import net.evilblock.cubed.serializers.Serializers
import org.bson.Document
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.system.exitProcess


// Add these data classes for performance monitoring
data class PerformanceMetrics(
    val documentsPerSecond: Double,
    val cpuUsage: Double,
    val memoryUsage: Double,
    val activeThreads: Int,
    val queueSize: Int
)

data class DynamicConfiguration(
    var batchSize: Int,
    var threadPoolSize: Int,
    var lastAdjustmentTime: Long = System.currentTimeMillis()
)

class ApplicationServerArgs(parser: ArgParser)
{
    val dev by parser
        .storing("--dev", help = "Whether this server is a dev server or not")
        .default("false")

    val redisHost by parser
        .storing(
            "--redishost",
            help = "The host of the Redis server"
        )
        .default("redis-master.databases.svc.cluster.local")

    val redisPort by parser
        .storing(
            "--redisport",
            help = "The port of the Redis server"
        ) {
            toInt()
        }
        .default(6379)

    val redisPassword by parser
        .storing(
            "--redispass",
            help = "The port of the Redis server"
        )
        .default("")

    val mongoDatabase by parser
        .storing(
            "--mongodatabase",
            help = "The database for the Mongo server"
        )
        .default("Scala")

    val mongoUri by parser
        .storing(
            "--mongouri",
            help = "The mongodb URI"
        )
        .default("mongodb://127.0.0.1:27017")

    val sentryDSN by parser
        .storing(
            "--dsn",
            help = "sentry dsn"
        )

    val maxConcurrentBatches by parser
        .storing("--maxConcurrentBatches", help = "The maximum amount of concurrent batches") { toInt() }
        .default(15)
}

/**
 * Optional env-var overrides. When set, these win over the CLI args
 * (which themselves win over the hard-coded defaults). Lets k8s
 * deployments configure connection details via Secret/ConfigMap
 * envFrom without having to wire CLI args through the container
 * entrypoint. Set narrowly: existing prod CLI-arg invocations are
 * unaffected when these env vars are unset.
 */
private fun applyEnvOverrides(args: Array<String>): Array<String>
{
    val overrides = mutableListOf<String>()
    fun add(envKey: String, cliFlag: String) {
        System.getenv(envKey)?.takeIf { it.isNotEmpty() }?.let {
            // CLI arg only takes effect if not already present
            if (args.none { a -> a == cliFlag || a.startsWith("$cliFlag=") }) {
                overrides += cliFlag
                overrides += it
            }
        }
    }
    add("REDIS_HOST", "--redishost")
    add("REDIS_PORT", "--redisport")
    add("REDIS_PASSWORD", "--redispass")
    add("MONGODB_URI", "--mongouri")
    add("MONGODB_DATABASE", "--mongodatabase")
    add("SENTRY_DSN", "--dsn")
    return args + overrides.toTypedArray()
}

/**
 * @author GrowlyX
 * @since 9/23/2023
 */
fun main(args: Array<String>) = mainBody {
    val parsedArgs = ArgParser(applyEnvOverrides(args))
        .parseInto {
            ApplicationServerArgs(it)
        }

    devProvider = {
        parsedArgs.dev == "true"
    }

    AwareHub.configure(
        WrappedAwareUri(
            parsedArgs.redisHost,
            parsedArgs.redisPort,
            if (parsedArgs.redisPassword == "")
                null else parsedArgs.redisPassword
        )
    ) {
        Serializers.gson
    }

    ScalaCommons.registerBundle(PracticeAPIPlatform)
    SentryIntegration.initialize(
        dsn = parsedArgs.sentryDSN
    )

    RedisShared.keyValueCache
    ServerSync.configureIndependent()

    val aware = CommunicationGateway("dps")
        .configure { this }

    ScalaDataStoreShared.INSTANCE =
        object : ScalaDataStoreShared()
        {
            override fun getNewRedisConnection(): AbstractDataStoreRedisConnection
            {
                return object : AbstractDataStoreRedisConnection()
                {
                    override fun createNewConnection() = aware
                }
            }

            override fun getNewMongoConnection(): AbstractDataStoreMongoConnection {
                val mongoClientUri = MongoClientURI(runCatching { File("mongodb-uri").readText() }.getOrNull() ?: parsedArgs.mongoUri)
                val mongoClient = MongoClient(mongoClientUri)

                return UriDataStoreMongoConnection(
                    DataStoreMongoConnectionDetails(
                        database = mongoClientUri.database ?: parsedArgs.mongoDatabase
                    ),
                    mongoClient
                )
            }

            override fun debug(from: String, message: String)
            {
                AbstractDataStoreConnection.LOGGER.info("$from: $message")
            }

            override fun forceDisableRedisThreshold() = true
        }

    Zoo.build(ScalaCommons
        .bundle()
        .globals()
        .redis())

    ScalaStoreUuidCache.configure(
        DistributedRedisUuidCacheTranslator(),
        MojangDataResolver
    )

    MiniGameSerializers.configure()
    Metadata.writer()
        .withNamespace(namespace())
        .enableAutomatedPush()

    MapDataSync.load()
    KitDataSync.load()
    KitDataSync.Modern.load()
    KitGroupDataSync.load()

    GameManager.load()
    GameQueueManager.load()
    ReplicationManager.load()

    HostedWorldGatewayManager.load()

    RedisShared.applicationBridge.configure {
        listen("reboot-app") {
            exitProcess(1)
        }

        listen("migrate-profiles") {
            if (true)
            {
                return@listen
            }

            thread {
                println("Starting profile migration process...")

                val profilesCollection = ScalaDataStoreShared.INSTANCE
                    .getNewMongoConnection()
                    .getAppliedResource()
                    .getCollection("PracticeProfile")

                // Get total count for progress tracking
                val totalDocuments = profilesCollection.countDocuments()
                println("Total documents to process: $totalDocuments")

                if (totalDocuments == 0L) {
                    println("No documents found to migrate.")
                    return@thread
                }

                val processedCount = AtomicLong(0)
                val bulkWritesExecuted = AtomicLong(0)
                val documentsWritten = AtomicLong(0)
                val activeThreadsCount = AtomicInteger(0)

                // Dynamic configuration with strict limits
                val maxAllowedThreads = parsedArgs.maxConcurrentBatches
                val config = DynamicConfiguration(
                    batchSize = 500, // Start smaller
                    threadPoolSize = minOf(maxAllowedThreads, 3) // Start conservative
                )

                val startTime = System.currentTimeMillis()
                var lastProgressTime = startTime
                var lastProcessedCount = 0L

                // Flag to control status bar thread
                var migrationComplete = false

                // Create initial thread pool
                var executor = Executors.newFixedThreadPool(config.threadPoolSize)
                var completionService = ExecutorCompletionService<Unit>(executor)

                println("Starting with ${config.threadPoolSize} threads (max: $maxAllowedThreads) and batch size ${config.batchSize}")

                // Enhanced status bar thread with dynamic adjustment
                val statusThread = thread(name = "status-bar-thread") {
                    var lastReportedProcessed = 0L
                    var lastReportedBulkWrites = 0L

                    while (!migrationComplete) {
                        try {
                            Thread.sleep(5000) // Check every 5 seconds

                            val currentTime = System.currentTimeMillis()
                            val currentProcessed = processedCount.get()
                            val currentBulkWrites = bulkWritesExecuted.get()
                            val currentWritten = documentsWritten.get()
                            val elapsedTime = currentTime - startTime

                            // Calculate performance metrics
                            val timeSinceLastProgress = currentTime - lastProgressTime
                            val documentsProcessedSinceLastCheck = currentProcessed - lastProcessedCount
                            val documentsPerSecond = if (timeSinceLastProgress > 0) {
                                (documentsProcessedSinceLastCheck.toDouble() / (timeSinceLastProgress / 1000.0))
                            } else 0.0

                            val cpuUsage = getCpuUsage()
                            val memoryUsage = getMemoryUsage()
                            val currentActiveThreads = maxOf(0, activeThreadsCount.get()) // Prevent negative display
                            val totalBatches = (totalDocuments + config.batchSize - 1) / config.batchSize

                            val metrics = PerformanceMetrics(
                                documentsPerSecond = documentsPerSecond,
                                cpuUsage = cpuUsage,
                                memoryUsage = memoryUsage,
                                activeThreads = currentActiveThreads,
                                queueSize = 0
                            )

                            // Try to adjust configuration
                            val configChanged = adjustConfiguration(config, metrics, totalDocuments, processedCount, maxAllowedThreads)

                            if (configChanged) {
                                println("🔄 Configuration adjusted - Threads: ${config.threadPoolSize}, Batch: ${config.batchSize}")
                            }

                            if (currentProcessed > 0 || currentBulkWrites > 0) {
                                // Processing progress bar
                                val processedPercentage = (currentProcessed.toDouble() / totalDocuments * 100).toInt()
                                val processedStatusBar = createStatusBar(processedPercentage, 50)

                                // Bulk writes progress bar
                                val bulkWritePercentage = (currentBulkWrites.toDouble() / totalBatches * 100).toInt()
                                val bulkWriteStatusBar = createStatusBar(bulkWritePercentage, 50)

                                println("PROCESSED: [$processedStatusBar] $processedPercentage% ($currentProcessed/$totalDocuments)")
                                println("BULK WRITES: [$bulkWriteStatusBar] $bulkWritePercentage% ($currentBulkWrites/$totalBatches)")
                                println("Documents Written: $currentWritten")

                                // Performance metrics
                                println("Performance: ${String.format("%.2f", documentsPerSecond)} docs/sec")
                                if (cpuUsage >= 0) println("CPU Usage: ${String.format("%.1f", cpuUsage)}%")
                                println("Memory Usage: ${String.format("%.1f", memoryUsage)}%")
                                println("Active Threads: $currentActiveThreads/${config.threadPoolSize} (max: $maxAllowedThreads)")
                                println("Current Batch Size: ${config.batchSize}")

                                if (currentProcessed > lastReportedProcessed || currentBulkWrites > lastReportedBulkWrites) {
                                    val remainingDocs = totalDocuments - currentProcessed
                                    val estimatedRemainingTime = if (documentsPerSecond > 0) {
                                        (remainingDocs / documentsPerSecond * 1000).toLong()
                                    } else 0L

                                    println("Elapsed: ${formatTime(elapsedTime)} | ETA: ${if (estimatedRemainingTime > 0) formatTime(estimatedRemainingTime) else "calculating..."}")
                                } else {
                                    println("Elapsed: ${formatTime(elapsedTime)} | Status: Processing...")
                                }

                                // Warning if performance is degrading
                                if (documentsPerSecond < 1.0 && currentProcessed > 1000) {
                                    println("⚠️  WARNING: Performance degraded to ${String.format("%.2f", documentsPerSecond)} docs/sec")
                                    if (cpuUsage > 90) {
                                        println("   High CPU usage detected - reducing load")
                                    }
                                }

                                println("---")

                                lastReportedProcessed = currentProcessed
                                lastReportedBulkWrites = currentBulkWrites
                            }

                            lastProgressTime = currentTime
                            lastProcessedCount = currentProcessed

                        } catch (e: InterruptedException) {
                            break
                        } catch (e: Exception) {
                            println("Status thread error: ${e.message}")
                            // Reset the active thread count if it goes negative
                            if (activeThreadsCount.get() < 0) {
                                activeThreadsCount.set(0)
                            }
                        }
                    }
                }

                try {
                    // Process documents with proper thread management
                    val cursor = profilesCollection.find().batchSize(config.batchSize).iterator()
                    val documentBatch = mutableListOf<Document>()
                    var submittedTasks = 0
                    var completedTasks = 0
                    val maxQueueSize = config.threadPoolSize * 2 // Limit queue size

                    while (cursor.hasNext() && !migrationComplete) {
                        documentBatch.add(cursor.next())

                        // Process batch when it reaches the desired size or we're at the end
                        if (documentBatch.size >= config.batchSize || !cursor.hasNext()) {
                            // Wait if we have too many pending tasks (backpressure)
                            while ((submittedTasks - completedTasks) >= maxQueueSize && !migrationComplete) {
                                try {
                                    val future = completionService.poll(100, TimeUnit.MILLISECONDS)
                                    if (future != null) {
                                        future.get() // Process completed task
                                        completedTasks++
                                    }
                                } catch (e: Exception) {
                                    println("Error in completed task: ${e.message}")
                                    completedTasks++
                                }
                            }

                            if (!migrationComplete) {
                                val batch = documentBatch.toList() // Create immutable copy
                                documentBatch.clear()

                                // Submit batch processing task with proper thread tracking
                                completionService.submit(Callable {
                                    val threadId = Thread.currentThread().id
                                    try {
                                        activeThreadsCount.incrementAndGet()
                                        processBatch(batch, profilesCollection, processedCount, bulkWritesExecuted, documentsWritten, totalDocuments, startTime)
                                    } catch (e: Exception) {
                                        println("Error in batch processing (thread $threadId): ${e.message}")
                                        throw e
                                    } finally {
                                        // Ensure we always decrement, but don't go negative
                                        if (activeThreadsCount.get() > 0) {
                                            activeThreadsCount.decrementAndGet()
                                        }
                                    }
                                })
                                submittedTasks++
                            }
                        }

                        // Yield occasionally to prevent tight loop
                        if (submittedTasks % 10 == 0) {
                            Thread.sleep(1)
                        }
                    }

                    cursor.close()

                    // Wait for all remaining tasks to complete
                    while (completedTasks < submittedTasks) {
                        try {
                            val future = completionService.poll(1, TimeUnit.SECONDS)
                            if (future != null) {
                                future.get()
                                completedTasks++
                            }
                        } catch (e: Exception) {
                            println("Error waiting for task completion: ${e.message}")
                            completedTasks++
                        }
                    }

                    // Mark migration as complete and wait for status thread to finish
                    migrationComplete = true
                    statusThread.join(5000) // Wait up to 5 seconds for status thread to finish

                    val totalTime = System.currentTimeMillis() - startTime
                    println("\n✅ Migration completed successfully!")
                    println("Total documents processed: ${processedCount.get()}")
                    println("Total bulk writes executed: ${bulkWritesExecuted.get()}")
                    println("Total documents written: ${documentsWritten.get()}")
                    println("Total time: ${formatTime(totalTime)}")
                    println("Average processing speed: ${String.format("%.2f", processedCount.get().toDouble() / (totalTime / 1000.0))} docs/sec")
                    println("Average write speed: ${String.format("%.2f", documentsWritten.get().toDouble() / (totalTime / 1000.0))} docs/sec")
                    println("Final configuration: ${config.threadPoolSize} threads, batch size ${config.batchSize}")

                } catch (e: Exception) {
                    println("❌ Error during migration: ${e.message}")
                    e.printStackTrace()
                    migrationComplete = true
                } finally {
                    migrationComplete = true
                    statusThread.interrupt()

                    executor.shutdown()
                    try {
                        if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                            executor.shutdownNow()
                        }
                    } catch (ie: InterruptedException) {
                        executor.shutdownNow()
                    }
                }
            }
        }
    }

    while (true)
    {
        Thread.sleep(Long.MAX_VALUE)
    }
}

private fun processProfileDocument(document: org.bson.Document): org.bson.Document {
    // Extract bot statistics (the main kit stats)
    val casualStatistics = document.get("casualStatistics") as? org.bson.Document
    val casualStatisticsMap = mutableMapOf<String, Map<TrackedKitStatistic, Int>>()

    casualStatistics?.let { botStats ->
        for ((kitName, kitData) in botStats) {
            if (kitData is org.bson.Document) {
                val stats = mutableMapOf<TrackedKitStatistic, Int>()

                // Map document fields to TrackedKitStatistic enum
                val fieldMappings = mapOf(
                    "plays" to TrackedKitStatistic.Plays,
                    "wins" to TrackedKitStatistic.Wins,
                    "kills" to TrackedKitStatistic.Kills,
                    "deaths" to TrackedKitStatistic.Deaths,
                    "longestStreak" to TrackedKitStatistic.WinStreakHighest,
                    "streak" to TrackedKitStatistic.WinStreak
                )

                fieldMappings.forEach { (fieldName, statistic) ->
                    kitData.getInteger(fieldName)?.let { value ->
                        stats[statistic] = value
                    }
                }

                stats[TrackedKitStatistic.Losses] =
                    (stats[TrackedKitStatistic.Plays] ?: 0) - (stats[TrackedKitStatistic.Wins] ?: 0)

                // Only add if we have stats
                if (stats.isNotEmpty()) {
                    casualStatisticsMap[kitName] = stats
                }
            }
        }
    }

    val rankedStatistics = document.get("rankedStatistics") as? org.bson.Document
    val rankedStatisticsMap = mutableMapOf<String, Map<TrackedKitStatistic, Int>>()

    rankedStatistics?.let { botStats ->
        for ((kitName, kitData) in botStats) {
            if (kitData is org.bson.Document) {
                val stats = mutableMapOf<TrackedKitStatistic, Int>()

                // Map document fields to TrackedKitStatistic enum
                val fieldMappings = mapOf(
                    "plays" to TrackedKitStatistic.Plays,
                    "wins" to TrackedKitStatistic.Wins,
                    "kills" to TrackedKitStatistic.Kills,
                    "deaths" to TrackedKitStatistic.Deaths,
                    "longestStreak" to TrackedKitStatistic.WinStreakHighest,
                    "streak" to TrackedKitStatistic.WinStreak,
                    "elo" to TrackedKitStatistic.ELO,
                )

                fieldMappings.forEach { (fieldName, statistic) ->
                    kitData.getInteger(fieldName)?.let { value ->
                        stats[statistic] = value
                    }
                }

                stats[TrackedKitStatistic.Losses] =
                    (stats[TrackedKitStatistic.Plays] ?: 0) - (stats[TrackedKitStatistic.Wins] ?: 0)

                // Only add if we have stats
                if (stats.isNotEmpty()) {
                    rankedStatisticsMap[kitName] = stats
                }
            }
        }
    }

    // Extract global statistics
    val globalStatistics = document.get("globalStatistics") as? org.bson.Document
    val globalStats = mutableMapOf<TrackedKitStatistic, Int>()

    globalStatistics?.let { globalStatsDoc ->
        // Map global statistics to TrackedKitStatistic enum
        val globalFieldMappings = mapOf(
            "totalPlays" to TrackedKitStatistic.Plays,
            "totalWins" to TrackedKitStatistic.Wins,
            "totalLosses" to TrackedKitStatistic.Losses,
            "totalKills" to TrackedKitStatistic.Kills,
            "totalDeaths" to TrackedKitStatistic.Deaths
        )

        globalFieldMappings.forEach { (fieldName, statistic) ->
            globalStatsDoc.getInteger(fieldName)?.let { value ->
                globalStats[statistic] = value
            }
        }
    }

    // Process the extracted data (modifies the document in place)
    processProfileData(document, casualStatisticsMap, rankedStatisticsMap, globalStats)

    document.remove("globalStatistics")
    document.remove("casualStatistics")
    document.remove("botStatistics")
    document.remove("rankedStatistics")

    // Return the modified document
    return document
}

fun processBatch(
    batch: List<Document>,
    profilesCollection: MongoCollection<Document>,
    processedCount: AtomicLong,
    bulkWritesExecuted: AtomicLong,
    documentsWritten: AtomicLong,
    totalDocuments: Long,
    startTime: Long
) {
    val bulkWrites = mutableListOf<WriteModel<Document>>()

    batch.forEach { document ->
        try {
            // Process the document and get the modified version
            val modifiedDocument = if (document.get("statistics") != null)
                document else processProfileDocument(document)

            // Add to bulk write operations
            val filter = Document("_id", document.getString("_id"))
            val replaceOperation = ReplaceOneModel(filter, modifiedDocument)
            bulkWrites.add(replaceOperation)

            // Increment processed count
            processedCount.incrementAndGet()
        } catch (e: Exception) {
            println("Error processing document ${document.getString("_id")}: ${e.message}")
            e.printStackTrace()
            // Still increment processed count even on error
            processedCount.incrementAndGet()
        }
    }

    // Execute bulk write
    if (bulkWrites.isNotEmpty()) {
        try {
            val result = profilesCollection.bulkWrite(bulkWrites)
            bulkWritesExecuted.incrementAndGet()
            documentsWritten.addAndGet(result.modifiedCount.toLong())

            // Add debug logging for mismatches
            if (result.modifiedCount != bulkWrites.size) {
                println("Warning: Expected to modify ${bulkWrites.size} documents, but only modified ${result.modifiedCount}")
            }
        } catch (e: Exception) {
            println("Error executing bulk write: ${e.message}")
            e.printStackTrace()
            // Still increment bulk writes executed counter to show we attempted it
            bulkWritesExecuted.incrementAndGet()
        }
    } else {
        println("Warning: No documents to write in batch")
        // Still increment bulk writes executed since we "processed" this batch
        bulkWritesExecuted.incrementAndGet()
    }
}

private fun processProfileData(
    document: Document,
    casualStatistics: Map<String, Map<TrackedKitStatistic, Int>>,
    rankedStatistics: Map<String, Map<TrackedKitStatistic, Int>>,
    globalStats: Map<TrackedKitStatistic, Int>
) {
    casualStatistics.forEach { (kitName, stats) ->
        stats.forEach { (statistic, value) ->
            statisticIdFrom(statistic) {
                kit(kitName)
                casual()
            }.let { statisticID ->
                RawStatisticCRUD.set(
                    profile = document,
                    newValue = value.toLong(),
                    id = statisticID
                )
            }

            statisticIds {
                kits(kitName)
                types(statistic)
                globalQueueType()
            }.forEach { statisticID ->
                RawStatisticCRUD.add(
                    profile = document,
                    addValue = value.toLong(),
                    id = statisticID
                )
            }
        }
    }

    rankedStatistics.forEach { (kitName, stats) ->
        stats.forEach { (statistic, value) ->
            statisticIdFrom(statistic) {
                kit(kitName)
                ranked()
            }.let { statisticID ->
                RawStatisticCRUD.set(
                    profile = document,
                    newValue = value.toLong(),
                    id = statisticID
                )
            }

            statisticIds {
                kits(kitName)
                types(statistic)
                globalQueueType()
            }.forEach { statisticID ->
                RawStatisticCRUD.add(
                    profile = document,
                    addValue = value.toLong(),
                    id = statisticID
                )
            }
        }
    }

    globalStats.forEach { (statistic, value) ->
        statisticIds {
            globalKit()
            globalQueueType()
            types(statistic)
            casual() // We're gonna mark all global stats as casual as well just for player sanity
        }.forEach { statisticID ->
            RawStatisticCRUD.set(
                profile = document,
                newValue = value.toLong(),
                id = statisticID
            )
        }
    }
}

private fun createStatusBar(percentage: Int, length: Int): String {
    // Ensure percentage is within valid bounds
    val validPercentage = percentage.coerceIn(0, 100)
    val validLength = maxOf(1, length)

    val filled = (validPercentage * validLength / 100).coerceIn(0, validLength)
    val empty = (validLength - filled).coerceIn(0, validLength)

    return "█".repeat(filled) + "░".repeat(empty)
}


private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m ${seconds % 60}s"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

// Add these helper functions with proper limits
private fun getCpuUsage(): Double {
    return try {
        val osBean = ManagementFactory.getOperatingSystemMXBean()
        when {
            osBean is com.sun.management.OperatingSystemMXBean -> {
                val cpuLoad = osBean.processCpuLoad
                if (cpuLoad < 0) -1.0 else cpuLoad * 100
            }
            else -> -1.0 // CPU usage not available
        }
    } catch (e: Exception) {
        -1.0
    }
}

private fun getMemoryUsage(): Double {
    return try {
        val memoryBean = ManagementFactory.getMemoryMXBean()
        val heapUsed = memoryBean.heapMemoryUsage.used
        val heapMax = memoryBean.heapMemoryUsage.max
        if (heapMax <= 0) 0.0 else (heapUsed.toDouble() / heapMax) * 100
    } catch (e: Exception) {
        0.0
    }
}

private fun adjustConfiguration(
    config: DynamicConfiguration,
    metrics: PerformanceMetrics,
    totalDocuments: Long,
    processedCount: AtomicLong,
    maxAllowedThreads: Int
): Boolean {
    val currentTime = System.currentTimeMillis()
    val timeSinceLastAdjustment = currentTime - config.lastAdjustmentTime

    // Only adjust every 30 seconds to allow changes to take effect
    if (timeSinceLastAdjustment < 30000) return false

    val remainingDocuments = totalDocuments - processedCount.get()
    var adjusted = false

    // Apply strict limits
    val minThreads = 1
    val maxThreads = minOf(maxAllowedThreads, Runtime.getRuntime().availableProcessors() * 2)
    val minBatchSize = 50
    val maxBatchSize = 1500

    // If CPU usage is too high, reduce load
    if (metrics.cpuUsage > 85.0) {
        if (config.threadPoolSize > minThreads) {
            config.threadPoolSize = maxOf(minThreads, config.threadPoolSize - 1)
            println("⚠️  High CPU usage (${String.format("%.1f", metrics.cpuUsage)}%), reducing threads to ${config.threadPoolSize}")
            adjusted = true
        } else if (config.batchSize > minBatchSize) {
            config.batchSize = maxOf(minBatchSize, (config.batchSize * 0.8).toInt())
            println("⚠️  High CPU usage (${String.format("%.1f", metrics.cpuUsage)}%), reducing batch size to ${config.batchSize}")
            adjusted = true
        }
    }
    // If memory usage is too high, reduce batch size
    else if (metrics.memoryUsage > 80.0) {
        if (config.batchSize > minBatchSize) {
            config.batchSize = maxOf(minBatchSize, (config.batchSize * 0.8).toInt())
            println("⚠️  High memory usage (${String.format("%.1f", metrics.memoryUsage)}%), reducing batch size to ${config.batchSize}")
            adjusted = true
        }
    }
    // If performance is good and we have capacity, try to increase throughput
    else if (metrics.cpuUsage >= 0 && metrics.cpuUsage < 60.0 && metrics.memoryUsage < 60.0 && metrics.documentsPerSecond > 0) {
        // Only increase if we have significant work remaining
        if (remainingDocuments > config.batchSize * 10) {
            if (config.threadPoolSize < maxThreads) {
                config.threadPoolSize = minOf(maxThreads, config.threadPoolSize + 1)
                println("📈 Good performance, increasing threads to ${config.threadPoolSize}")
                adjusted = true
            } else if (config.batchSize < maxBatchSize) {
                config.batchSize = minOf(maxBatchSize, (config.batchSize * 1.1).toInt())
                println("📈 Good performance, increasing batch size to ${config.batchSize}")
                adjusted = true
            }
        }
    }

    // Ensure values stay within bounds
    config.threadPoolSize = config.threadPoolSize.coerceIn(minThreads, maxThreads)
    config.batchSize = config.batchSize.coerceIn(minBatchSize, maxBatchSize)

    if (adjusted) {
        config.lastAdjustmentTime = currentTime
    }

    return adjusted
}
