package gg.tropic.practice.map

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Close
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.games.GameImpl
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.bots.deleteBotMetadataOfPlayer
import gg.tropic.practice.games.bots.getBotMetdataOfPlayer
import gg.tropic.practice.games.robot.RobotGameLifecycle
import gg.tropic.practice.kit.findKitAcrossStores
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.map.metadata.impl.MapPortalMetadata
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameRegistry
import gg.tropic.practice.minigame.tasks.MiniGamePreStartTask.Companion.startPreStartTask
import gg.tropic.practice.replication.MapReplication
import gg.tropic.practice.replication.ReplicationRPC
import gg.tropic.practice.replication.ReplicationResultStatus
import gg.tropic.practice.replication.ServerAvailableReplicationState
import gg.tropic.practice.replication.generation.ReplicationGenerationRequestHandler
import gg.tropic.practice.replication.generation.ReplicationGeneratorService
import gg.tropic.practice.replication.generation.rpc.GenerationResult
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.versioned.Versioned
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.ServerVersion
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.metadata.FixedMetadataValue
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write
import kotlin.math.max

/**
 * @author GrowlyX
 * @since 9/21/2023
 */
@Service(priority = 20)
object MapReplicationService
{
    @Inject
    lateinit var plugin: ExtendedScalaPlugin

    private val readyMaps = mutableMapOf<String, ReadyMapTemplate>()

    private val mapLock = ReentrantReadWriteLock()
    private val mapReplications = mutableListOf<BuiltMapReplication>()

    fun readReplications() = mapLock.read { mapReplications.toList() }
    fun writeReplications(write: MutableList<BuiltMapReplication>.() -> Unit) = mapLock.write { write(mapReplications) }

    fun removeReplicationMatchingWorld(world: World) = writeReplications {
        removeIf {
            it.world?.name == world.name || it.world == null
        }
    }

    @Configure
    fun configure()
    {
        populateSlimeCache()

        val buildNewReplication: (Map, GameExpectation) -> CompletableFuture<GenerationResult> = { map, expectation ->
            generateArenaWorld(map)
                .thenApply { repl ->
                    if (repl == null)
                    {
                        return@thenApply GenerationResult(
                            status = ReplicationResultStatus.FAILED,
                            "Failed to generate map replication"
                        )
                    }

                    repl.scheduledForExpectedGame = expectation.identifier
                    writeReplications {
                        this += repl
                    }

                    buildGameResources(expectation)
                }
                .exceptionally { throwable ->
                    throwable.printStackTrace()
                    return@exceptionally null
                }
        }

        val allocateExistingReplication: (Map, GameExpectation) -> CompletableFuture<GenerationResult> =
            scope@{ map, expectation ->
                val replication = readReplications()
                    .firstOrNull {
                        it.associatedMap.name == map.name && !it.inUse
                            && it.scheduledForExpectedGame == null
                    }
                    ?: return@scope run {
                        CompletableFuture.completedFuture(null)
                    }

                replication.scheduledForExpectedGame = expectation.identifier
                return@scope CompletableFuture.completedFuture(buildGameResources(expectation))
            }

        ReplicationRPC.generationService.addHandler(
            ReplicationGenerationRequestHandler({ buildNewReplication }, { allocateExistingReplication })
        )

        ReplicationGeneratorService.bindToStatusService {
            val replicationStatuses = readReplications()
                .toList()
                .filter { it.world != null }
                .map {
                    MapReplication(
                        associatedMapName = it.associatedMap.name,
                        name = it.world!!.name,
                        // Treat scheduled-but-not-yet-started replications as in-use
                        // so the queue's allocator path doesn't race against them.
                        inUse = it.inUse || it.scheduledForExpectedGame != null,
                        server = ServerSync.local.id
                    )
                }
                .groupBy {
                    it.associatedMapName
                }

            ServerAvailableReplicationState(replicationStatuses)
        }

        MapService.onPostReload = ::populateSlimeCache
        startWorldRequestThread()

//        ReplicationAutoScaleTask.start()
    }

    @Close
    fun close()
    {
//        ReplicationAutoScaleTask.interrupt()
    }

    fun findScheduledReplication(expectation: UUID) = readReplications()
        .firstOrNull { it.scheduledForExpectedGame == expectation }

    fun findAllAvailableReplications(map: Map) = readReplications()
        .filter {
            !it.inUse && it.associatedMap.name == map.name
        }

    fun generateMapReplications(
        mappings: kotlin.collections.Map<Map, Int>
    ): CompletableFuture<Void>
    {
        return CompletableFuture.allOf(
            *mappings.entries
                .flatMap {
                    (0 until it.value)
                        .map { _ ->
                            generateArenaWorld(it.key)
                                .thenAccept { replication ->
                                    writeReplications {
                                        this += replication
                                    }
                                }
                        }
                }
                .toTypedArray()
        )
    }

    fun startIfReady(game: GameImpl): Boolean
    {
        if (game.robot())
        {
            if (
                game.humanSide().toBukkitPlayers().all { it != null } &&
                game.humanSide().players.all { it in game.readyPlayers } &&
                game.robotInstance.isNotEmpty()
            )
            {
                game.initializeAndStart()
                return true
            }
            return false
        }

        // Require both Bukkit.getPlayer() non-null AND PlayerJoinEvent to have fired.
        // Bukkit.getPlayer() can return non-null during the login handshake before
        // the player is fully connected, causing the "ghost player" / "0 ms" bug.
        if (
            game.toBukkitPlayers()
                .none { other ->
                    other == null
                } &&
            game.toPlayers().all { it in game.readyPlayers }
        )
        {
            game.initializeAndStart()
            return true
        }

        return false
    }

    fun buildGameResources(expectation: GameExpectation): GenerationResult
    {
        val kit = findKitAcrossStores(expectation.kitId)
            ?: return GenerationResult(
                status = ReplicationResultStatus.FAILED,
                "System could not find the kit for your match"
            )

        fun flagMetaData(flag: FeatureFlag, key: String): String?
        {
            if (!kit.features(flag))
            {
                return null
            }

            return kit
                .features[flag]
                ?.get(key)
                ?: flag.schema[key]
        }

        val meta = flagMetaData(FeatureFlag.MiniGameType, "id")
        val miniGameOrchestrator = meta
            ?.let {
                MiniGameRegistry
                    .miniGameOrchestrators[it]
            }

        val scheduledMap = findScheduledReplication(expectation.identifier)
            ?: return GenerationResult(
                status = ReplicationResultStatus.FAILED,
                "System was unable to find the respective map replication"
            )

        if (scheduledMap.world == null)
        {
            return GenerationResult(
                status = ReplicationResultStatus.FAILED,
                "System created your match improperly"
            )
        }

        val newGame = if (kit.lifecycle() != GameLifecycle.MiniGame)
        {
            GameImpl(
                expectation = expectation,
                kit = kit,
                arenaWorld = scheduledMap.world
            ).apply {
                if (expectation.freeForAll)
                {
                    isFreeForAll = true
                    shouldAllowFriendlyFire = true
                }
            }
        } else
        {
            miniGameOrchestrator?.construct(scheduledMap.world, kit, expectation)
                ?: return GenerationResult(
                    status = ReplicationResultStatus.FAILED,
                    "Minigame system was unable to construct a game"
                )
        }

        newGame.map.metadata.clearSignLocations(scheduledMap.world)

        Schedulers
            .sync()
            .run {
                newGame._map.prepare(scheduledMap.world)
            }
            .join()

        scheduledMap.inUse = true

        if (!newGame.buildResources())
        {
            newGame.state = GameState.Completed
            newGame.closeAndCleanup()

            return GenerationResult(
                status = ReplicationResultStatus.FAILED,
                "System was unable to build game resources"
            )
        }

        if (newGame.robot())
        {
            newGame.botGameMetadata = getBotMetdataOfPlayer(newGame.humanSide().players.first())
            deleteBotMetadataOfPlayer(newGame.humanSide().players.first())

            kotlin.runCatching {
                newGame.robotInstance.addAll(RobotGameLifecycle.createNewRobotInstances(newGame))
                newGame.robotInstance.forEach {
                    it.terminable.bindWith(newGame)
                }

                Schedulers.sync()
                    .run {
                        for (robotInstance in newGame.robotInstance)
                        {
                            if (!robotInstance.hasInitialSpawned)
                            {
                                robotInstance.apply {
                                    runCatching {
                                        initialSpawn()
                                    }.onFailure { throwable ->
                                        throwable.printStackTrace()

                                        newGame.state = GameState.Completed
                                        newGame.closeAndCleanup()
                                        return@run
                                    }
                                    hasInitialSpawned = true
                                }
                            }
                        }
                    }
                    .join()
            }.onFailure {
                it.printStackTrace()

                newGame.state = GameState.Completed
                newGame.closeAndCleanup()

                return GenerationResult(
                    status = ReplicationResultStatus.FAILED,
                    "Robot system was unable to create a rboot"
                )
            }

            if (newGame.robotInstance.isEmpty())
            {
                newGame.state = GameState.Completed
                newGame.closeAndCleanup()

                return GenerationResult(
                    status = ReplicationResultStatus.FAILED,
                    "Robot system expected robots, but none were created"
                )
            }
        }

        if (newGame.miniGameLifecycle == null)
        {
            val start = System.currentTimeMillis()
            val expectedTimeoutMillis = max(
                5000L,
                750L * (if (newGame.robot()) newGame.humanSide().players.size else newGame.toPlayers().size)
            )

            Schedulers
                .async()
                .runRepeating(
                    { task ->
                        if (System.currentTimeMillis() >= start + expectedTimeoutMillis)
                        {
                            newGame.state = GameState.Completed
                            newGame.closeAndCleanup()

                            task.closeAndReportException()
                            return@runRepeating
                        }

                        if (newGame.state == GameState.Waiting)
                        {
                            if (startIfReady(newGame))
                            {
                                task.closeAndReportException()
                            }
                        }
                    },
                    0L, 1L
                )
        } else
        {
            if (newGame is AbstractMiniGameGameImpl<*>)
            {
                newGame.startPreStartTask()
            }
        }

        GameService.gameMappings[expectation.identifier] = newGame
        return GenerationResult(
            status = ReplicationResultStatus.COMPLETED,
            message = "System prepared the game successfully"
        )
    }

    private const val TARGET_PRE_GEN_REPLICATIONS = 16
    private fun preGenerateMapReplications(): CompletableFuture<Void>
    {
        return generateMapReplications(
            MapService.maps().filter { it.version == fleetVersion }
                .associateWith { TARGET_PRE_GEN_REPLICATIONS }
        )
    }

    private val fleetVersion: MiniProviderVersion =
        if (ServerVersion.getVersion().isOlderThan(ServerVersion.v1_9))
            MiniProviderVersion.LEGACY
        else
            MiniProviderVersion.MODERN

    private fun populateSlimeCache()
    {
        for (arena in MapService.maps())
        {
            if (arena.version != fleetVersion) continue

            kotlin.runCatching {
                readyMaps[arena.name] = ReadyMapTemplate(
                    slimeWorld = Versioned.toProvider()
                        .getSlimeProvider()
                        .loadReadOnlyWorld(arena.associatedSlimeTemplate)
                )

                plugin.logger.info(
                    "Populated slime cache with SlimeWorld for arena ${arena.name}."
                )
            }.onFailure {
                plugin.logger.log(
                    Level.SEVERE, "Failed to populate cache for ${arena.name}", it
                )
            }
        }
    }

    private fun versionedCurrentTickProvider() =
        Versioned.toProvider().getServerProvider().currentTick()

    fun startWorldRequestThread() = thread(isDaemon = true) {
        var lastRecordedTick = versionedCurrentTickProvider()
        var lastTickSwitch = System.currentTimeMillis()
        var lastBroadcastTick = System.currentTimeMillis()

        while (true)
        {
            if (versionedCurrentTickProvider() != lastRecordedTick)
            {
                if (System.currentTimeMillis() - lastTickSwitch > 5000L)
                {
                    plugin.logger.info("Pausing generation checks for 3s to let the server catch up...")
                    Thread.sleep(3000L)
                }

                lastRecordedTick = versionedCurrentTickProvider()
                lastTickSwitch = System.currentTimeMillis()
            }

            if (System.currentTimeMillis() - lastTickSwitch > 5000L)
            {
                if (System.currentTimeMillis() - lastBroadcastTick > 5000L)
                {
                    lastBroadcastTick = System.currentTimeMillis()
                    plugin.logger.info("Server is halted at tick $lastRecordedTick... Waiting before expiring any pending generations")
                }
                continue
            }

            for (worldID in worldRequests.toMap().keys)
            {
                val bukkitWorld = Bukkit.getWorld(worldID.first)
                if (bukkitWorld == null)
                {
                    if (System.currentTimeMillis() - worldID.second >= 15_000L)
                    {
                        worldRequests.remove(worldID)
                            ?.completeExceptionally(
                                IllegalStateException(
                                    "Could not load world ${worldID.first}"
                                )
                            )
                        continue
                    }
                    continue
                }

                worldRequests.remove(worldID)?.complete(bukkitWorld)
            }

            Thread.sleep(350L)
        }
    }

    private val worldRequests = ConcurrentHashMap<Pair<String, Long>, CompletableFuture<World>>()
    fun submitWorldRequest(worldID: String): CompletableFuture<World>
    {
        val future = CompletableFuture<World>()
        worldRequests[worldID to System.currentTimeMillis()] = future

        return future.orTimeout(5L, TimeUnit.SECONDS)
    }

    private fun Map.prepare(world: World)
    {
        metadata.metadata
            .filterIsInstance<MapPortalMetadata>()
            .forEach {
                for (block in it.bounds.utility(world))
                {
                    block.type = XMaterial.END_PORTAL.get()
                    block.setMetadata(
                        "tropicportal",
                        FixedMetadataValue(plugin, it.id)
                    )
                }
            }
    }

    fun runInWorldLoadSync(run: Runnable) = CompletableFuture
        .runAsync(run, syncWorldLoadScheduler)

    private val syncWorldLoadScheduler = Executors.newSingleThreadScheduledExecutor()
    private fun generateArenaWorld(arena: Map): CompletableFuture<BuiltMapReplication>
    {
        val worldName = "minigame_instance_${arena.name}_${
            UUID.randomUUID().toString().substring(0..5)
        }"

        val readyMap = readyMaps[arena.name]
            ?: return CompletableFuture.failedFuture(
                IllegalStateException("Map ${arena.name} does not have a ready SlimeWorld. Map changes have not propagated to this server?")
            )

        return runInWorldLoadSync {
            Versioned
                .toProvider()
                .getSlimeProvider()
                .queueGenerateWorld(readyMap.slimeWorld, worldName)
        }.thenCompose {
            submitWorldRequest(worldName)
        }.thenApply {
            if (ServerVersion.getVersion().isNewerThan(ServerVersion.v1_9))
            {
                Schedulers
                    .sync()
                    .run {
                        it.setGameRuleValue("doDaylightCycle", "false")
                        it.setGameRuleValue("announceAdvancements", "false")
                        // skip the vanilla death screen so players are respawned
                        // straight into the game's spectator/respawn flow
                        it.setGameRuleValue("doImmediateRespawn", "true")
                    }
                    .join()
            } else
            {
                it.setGameRuleValue("doDaylightCycle", "false")
                it.setGameRuleValue("announceAdvancements", "false")
            }

            BuiltMapReplication(arena, it)
        }.exceptionally {
            plugin.logger.log(
                Level.SEVERE,
                "Could not load/generate world $worldName",
                (it as CompletionException).cause ?: it
            )
            return@exceptionally null
        }
    }
}
