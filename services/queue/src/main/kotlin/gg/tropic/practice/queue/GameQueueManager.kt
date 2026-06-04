package gg.tropic.practice.queue

import gg.scala.cache.uuid.ScalaStoreUuidCache
import gg.scala.commons.ScalaCommons
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.agnostic.sync.server.state.ServerState
import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.application.api.defaults.kit.KitDataSync
import gg.tropic.practice.application.api.defaults.map.ImmutableMap
import gg.tropic.practice.application.api.defaults.map.MapDataSync
import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.extensions.formatPlayerPing
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.duels.DuelRequest
import gg.tropic.practice.games.manager.GameManager
import gg.tropic.practice.games.manager.strategies.ServerSelectionStrategy
import gg.tropic.practice.games.spectate.PlayerSpectateRequest
import gg.tropic.practice.games.spectate.SpectateRequest
import gg.tropic.practice.games.spectate.SpectateResponseStatus
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.lobbyGroup
import gg.tropic.practice.minigame.MiniGameRPC
import gg.tropic.practice.namespace
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.provider.MiniProviderType
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.queue.variants.BedWarsSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.ArcadeSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.HungerGamesSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.MiniWallsSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.PofSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.SkyWarsSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.variants.robot.SubscribableDuoRobotPlayerQueue
import gg.tropic.practice.queue.variants.robot.SubscribableSoloRobotPlayerQueue
import gg.tropic.practice.region.Region
import gg.tropic.practice.replication.ReplicationResultStatus
import gg.tropic.practice.replication.generation.rpc.GenerationRequirement
import gg.tropic.practice.replications.manager.ReplicationManager
import gg.tropic.practice.serializable.Message
import gg.tropic.practice.suffixWhenDev
import io.sentry.Sentry
import io.sentry.SpanStatus
import mc.arch.commons.communications.rpc.CommunicationGateway
import mc.arch.minigame.bedwars.neo.BedWarsMode
import mc.arch.minigame.miniwalls.MiniWallsMode
import mc.arch.minigames.pof.PofMode
import mc.arch.minigames.hungergames.HungerGamesMode
import mc.arch.minigames.arcade.ArcadeMode
import mc.arch.minigames.skywars.SkyWarsMode
import net.evilblock.cubed.serializers.Serializers
import net.md_5.bungee.api.chat.ClickEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

/**
 * @author GrowlyX
 * @since 9/24/2023
 */
object GameQueueManager
{
    var forceSpecificRegionGames: Region? = null
    private val dpsQueueRedis = CommunicationGateway("gamequeue")

    private val dpsRedisCache = RedisShared.keyValueCache
    private val queueHolder = CentralSubscribablePlayerQueueHolder()

    private fun lookupKit(kitId: String): Pair<ImmutableKit, MiniProviderVersion>?
    {
        // scan for legacy gamemodes first, beacuse in order to allow
        // a cross-play system on modern duels we rely on the fact that
        // 1.21 clients can join legacy games. If there is no legacy implementation
        // for the kit, then we select modern
        KitDataSync.cached().kits[kitId]
            ?.let { return it to MiniProviderVersion.LEGACY }

        KitDataSync.Modern.cached().kits[kitId]
            ?.let { return it to MiniProviderVersion.MODERN }

        return null
    }

    fun prepareGameFor(
        map: ImmutableMap,
        expectation: GameExpectation,
        region: Region,
        excludeInstance: String? = null,
        blacklistedInstances: Set<String> = emptySet(),
        version: MiniProviderVersion = MiniProviderVersion.LEGACY,
        selectNewestInstance: Boolean = false
    ): CompletableFuture<Void>
    {
        // Start Sentry transaction for game preparation
        val transaction = Sentry.startTransaction(
            "minigame.prepare_game",
            "queue.prepare"
        ).apply {
            setData("map", map.name)
            setData("players", expectation.players.size)
            setData("region", region.name)
            setData("kit", expectation.kitId)
            setData("queue_id", expectation.queueId ?: "N/A")
        }

        fun fail(message: String)
        {
            transaction.setData("failure_reason", message)
            transaction.status = SpanStatus.INTERNAL_ERROR
            transaction.finish()

            RedisShared.sendMessage(
                expectation.players.toList(),
                listOf(
                    "&cWe were not able to send you to a game server! :(",
                    "&cPlease report the following message to an administrator: &f$message"
                )
            )
        }

        val distinctUsers = expectation.players.distinct()
        if (distinctUsers.size != expectation.players.size)
        {
            fail("Duplicate players are on teams")
            return CompletableFuture.completedFuture(null)
        }

        /**
         * Although we check for the map lock when searching for a random map,
         * we want to handle this edge case for duels and anything else.
         */
        if (map.locked)
        {
            fail("Map service gave an unavailable result for the map")
            return CompletableFuture.completedFuture(null)
        }

        /**
         * At this point, we have a [GameExpectation] that is saved in Redis, and
         * we've gotten rid of the queue entries from the list portion of queue. The players
         * still think they are in the queue, so we can generate the map and THEN update
         * their personal queue status. If they, for some reason, LEAVE the queue at this time, then FUCK ME!
         */
        val serverStatuses = ReplicationManager.allServerStatuses()
        val serverToReplicationMappings = serverStatuses.entries
            .filter {
                val game = ServerContainer
                    .getServer<GameServer?>(it.key)
                    ?: return@filter false

                game.lastHeartbeat + 5000L > System.currentTimeMillis() && game.state == ServerState.Loaded && !game.isDraining()
            }
            .flatMap {
                it.value.status.replications.values.flatten()
            }

        val availableReplication = serverToReplicationMappings
            .sortedBy {
                val game = ServerContainer
                    .getServer<GameServer?>(it.server)
                    ?: return@sortedBy Int.MAX_VALUE

                game.getPlayersCount() ?: Int.MAX_VALUE
            }
            .firstOrNull {
                !it.inUse && it.associatedMapName == map.name &&
                    Region.extractFrom(it.server)
                        .withinScopeOf(forceSpecificRegionGames ?: region)
            }

        // if there's an existing replication to house the game, we can send them directly
        // there. if not, we'll take the server with the least player count
        val serverToRequestReplication = availableReplication?.server
            ?: (ServerSelectionStrategy.select(
                requiredVersion = version,
                requiredType = MiniProviderType.MINIGAME,
                region = region,
                excludeInstance = excludeInstance,
                blacklistedInstances = blacklistedInstances,
                minigameOrchestratorID = expectation.miniGameConfiguration?.orchestratorID,
                selectNewestInstance = selectNewestInstance,
            )?.id)
            ?: return run {
                fail("No server available to host your minigame")
                CompletableFuture.completedFuture(null)
            }

        // Start replication span
        val replicationSpan = transaction.startChild("replication.generate", serverToRequestReplication)
        replicationSpan.setData("map", map.name)
        replicationSpan.setData("requirement", if (availableReplication == null) "GENERATE" else "ALLOCATE")

        return ReplicationManager
            .generateReplication(
                server = serverToRequestReplication,
                map = map.name,
                expectation = expectation,
                requirement = if (availableReplication == null)
                    GenerationRequirement.GENERATE else GenerationRequirement.ALLOCATE
            )
            .thenAcceptAsync {
                if (it.status == ReplicationResultStatus.COMPLETED)
                {
                    replicationSpan.status = SpanStatus.OK
                    replicationSpan.finish()
                    transaction.status = SpanStatus.OK
                    transaction.finish()

                    RedisShared.redirect(
                        expectation.players.toList(), serverToRequestReplication
                    )
                } else
                {
                    replicationSpan.setData("failure_message", it.message ?: "N/A")
                    replicationSpan.status = SpanStatus.INTERNAL_ERROR
                    replicationSpan.finish()

                    fail("${it.message ?: "N/A (Replication failure)"} (${serverToRequestReplication})")
                }
            }
            .exceptionally {
                Sentry.captureException(it)
                replicationSpan.throwable = it
                replicationSpan.status = SpanStatus.INTERNAL_ERROR
                replicationSpan.finish()
                transaction.throwable = it
                transaction.status = SpanStatus.INTERNAL_ERROR
                transaction.finish()

                it.printStackTrace()
                RedisShared.sendMessage(
                    expectation.players.toList(),
                    listOf(
                        "&cWe were not able to send you to a game server! :(",
                        "&cPlease report the following message to an administrator: &f${it.message ?: "N/A (Replication failure)"} (${serverToRequestReplication})"
                    )
                )
                return@exceptionally null
            }

    }

    fun playerIsOnline(uniqueId: UUID) = dpsRedisCache.sync()
        .hexists(
            "symphony:players",
            uniqueId.toString()
        )

    fun load()
    {
        KitDataSync.onReload {
            buildAndValidateQueueIndexes()
        }

        KitDataSync.Modern.onReload {
            buildAndValidateQueueIndexes()
        }

        buildAndValidateQueueIndexes()
        dpsRedisCache.sync().del("${namespace().suffixWhenDev()}:duelrequests:*")

        Logger.getGlobal().info("Invalidated existing duel requests")

        val executor = Executors.newScheduledThreadPool(3)
        queueHolder.configure(executor)

        val previouslyQueued = ScalaCommons.bundle().globals().redis()
            .sync()
            .hkeys("$queueV2Namespace:states")
            .map { UUID.fromString(it) }

        ScalaCommons.bundle().globals().redis()
            .sync()
            .del("$queueV2Namespace:states")

        RedisShared.sendMessage(
            previouslyQueued,
            Message()
                .withMessage("&c&lYou were removed from the queue as the system has restarted.")
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            println("Terminating all duel request invalidators before shutdown")
            executor.shutdownNow()
        })

        dpsQueueRedis.configure {
            listen("force-specific-region") {
                val regionID = retrieve<String>("region-id")
                forceSpecificRegionGames = if (regionID != "__RESET__")
                {
                    Region.valueOf(regionID)
                } else
                {
                    null
                }
            }

            val futureMappings = mutableMapOf<String, ScheduledFuture<*>>()
            listen("accept-duel") {
                val request = retrieve<DuelRequest>("request")

                val key = "${namespace().suffixWhenDev()}:duelrequests:${request.requester}:${request.kitID}"
                futureMappings[key]?.cancel(true)

                dpsRedisCache.sync().hdel(key, request.requestee.toString())

                if (!playerIsOnline(request.requester))
                {
                    RedisShared.sendMessage(
                        listOf(request.requestee),
                        listOf("&cThe player that sent you the duel request is no longer online!")
                    )
                    return@listen
                }

                val model = ServerContainer
                    .allServers<GameServer>()
                    .firstOrNull {
                        it.getMetadataValue<List<String>>(
                            "server", "online-list"
                        )!!.contains(
                            request.requester.toString()
                        )
                    }

                if (model == null || lobbyGroup().suffixWhenDev() !in model.groups)
                {
                    RedisShared.sendMessage(
                        listOf(request.requestee),
                        listOf("&cThe player that sent you the duel request is no longer on a practice lobby!")
                    )
                    return@listen
                }

                if (queueHolder.queueOfPlayer(request.requester) != null)
                {
                    RedisShared.sendMessage(
                        listOf(request.requestee),
                        listOf("&cThe player that sent you the duel request is currently queued for a game!")
                    )
                    return@listen
                }

                GameManager.allGames()
                    .firstOrNull { ref -> request.requester in ref.players }
                    .let {
                        if (it != null)
                        {
                            RedisShared.sendMessage(
                                listOf(request.requestee),
                                listOf("&cThe player that sent you the duel request is currently in a game!")
                            )
                            return@let
                        }

                        val (kit, kitVersion) = lookupKit(request.kitID)
                            ?: return@let run {
                                RedisShared.sendMessage(
                                    listOf(request.requestee),
                                    listOf(
                                        "&cThe kit you received a duel request for no longer exists!"
                                    )
                                )
                            }

                        // we need to do the check again, so why not
                        val map = if (request.mapID == null)
                        {
                            MapDataSync
                                .selectRandomMapCompatibleWith(kit, kitVersion)
                        } else
                        {
                            MapDataSync.cached().maps[request.mapID]
                        } ?: return@let run {
                            RedisShared.sendMessage(
                                listOf(request.requestee),
                                listOf(
                                    "&cWe found no map compatible with the kit you received a duel request for!"
                                )
                            )
                        }

                        prepareGameFor(
                            map = map,
                            expectation = GameExpectation(
                                players = listOf(request.requester, request.requestee).toMutableSet(),
                                identifier = UUID.randomUUID(),
                                teams = setOf(
                                    GameTeam(teamIdentifier = TeamIdentifier.A, mutableSetOf(request.requester)),
                                    GameTeam(teamIdentifier = TeamIdentifier.B, mutableSetOf(request.requestee))
                                ),
                                kitId = request.kitID,
                                mapId = map.name,
                                configuration = request.configuration
                            ),
                            region = request.region,
                            version = kitVersion
                        )
                    }
            }

            listen("create-match") {
                val config = retrieve<GameExpectation>("config")
                val mapID = retrieveNullable<String>("map")
                val kitID = retrieve<String>("kit")
                val region = Region.valueOf(retrieve<String>("region"))

                val (kit, kitVersion) = lookupKit(kitID)
                    ?: return@listen

                // we need to do the check again, so why not
                val map = if (mapID == null)
                {
                    MapDataSync
                        .selectRandomMapCompatibleWith(kit, kitVersion)
                } else
                {
                    MapDataSync.cached().maps[mapID]
                } ?: return@listen

                prepareGameFor(
                    map = map,
                    expectation = config,
                    region = region,
                    version = kitVersion
                )
            }

            listen("request-duel") {
                val request = retrieve<DuelRequest>("request")
                val key = "${namespace().suffixWhenDev()}:duelrequests:${request.requester}:${request.kitID}"
                dpsRedisCache.sync().hset(
                    key,
                    request.requestee.toString(),
                    Serializers.gson.toJson(request)
                )

                val kit = lookupKit(request.kitID)?.first!!
                val map = if (request.mapID != null)
                {
                    MapDataSync.cached().maps[request.mapID]
                } else null

                val requesterName = ScalaStoreUuidCache.username(request.requester)
                val requesterRegion = request.region

                val pingColor = formatPlayerPing(request.requesterPing)

                RedisShared.sendMessage(
                    listOf(request.requestee),
                    Message()
                        .withMessage(
                            " ",
                            "{primary}Duel Request:",
                            "&7┃ &fFrom: {primary}$requesterName &7(${pingColor}${request.requesterPing}ms&7)",
                            "&7┃ &fKit: {primary}${kit.displayName}",
                            "&7┃ &fMap: {primary}${map?.displayName ?: "Random"}",
                            "&7┃ &fRegion: {primary}$requesterRegion",
                            " "
                        )
                        .withMessage(
                            "&a(Click to accept)"
                        )
                        .andCommandOf(
                            ClickEvent.Action.RUN_COMMAND,
                            "/accept $requesterName ${kit.id}"
                        )
                        .andHoverOf("Click to accept!")
                        .withMessage("")
                )

                RedisShared.sendNotificationSound(
                    listOf(request.requestee),
                    "duel-sounds"
                )

                futureMappings[key] = executor.schedule({
                    RedisShared.sendMessage(
                        listOf(request.requestee),
                        listOf("&cYour duel request from &f${requesterName}&c with kit &f${kit.displayName}&c has expired!")
                    )

                    dpsRedisCache.sync().hdel(key, request.requestee.toString())
                }, 1L, TimeUnit.MINUTES)
            }

            listen("spectate") {
                val request = retrieve<PlayerSpectateRequest>("request")

                GameManager.allGames()
                    .firstOrNull { ref -> request.target in ref.players }
                    .let {
                        if (it == null)
                        {
                            RedisShared.sendMessage(
                                listOf(request.player),
                                listOf("&cThe player you tried to spectate is not in a game!")
                            )
                            return@let
                        }

                        if (it.state == GameState.Waiting || it.state == GameState.Starting)
                        {
                            RedisShared.sendMessage(
                                listOf(request.player),
                                listOf("&cThe game has not started yet!")
                            )
                            return@let
                        }

                        if (!it.majorityAllowsSpectators && !request.bypassesSpectatorAllowanceChecks)
                        {
                            RedisShared.sendMessage(
                                listOf(request.player),
                                listOf("&cThe game you tried to spectate has spectators disabled!")
                            )
                            return@let
                        }

                        if (it.miniGameType != null && !request.bypassesSpectatorAllowanceChecks)
                        {
                            RedisShared.sendMessage(
                                listOf(request.player),
                                listOf("&cThis minigame match cannot be spectated by players.")
                            )
                            return@let
                        }

                        MiniGameRPC.spectateService
                            .call(SpectateRequest(
                                server = it.server,
                                gameId = it.uniqueId,
                                player = request.player,
                                target = request.target,
                                bypassesSpectatorAllowanceChecks = request.bypassesSpectatorAllowanceChecks
                            ))
                            .whenComplete { response, throwable ->
                                if (throwable != null || response.status != SpectateResponseStatus.SUCCESS)
                                {
                                    RedisShared.sendMessage(
                                        listOf(request.player),
                                        listOf("&cWe weren't able to add you as a spectator. (${response?.status ?: "N/A"})")
                                    )
                                    return@whenComplete
                                }

                                RedisShared.redirect(
                                    listOf(request.player),
                                    it.server
                                )
                            }
                    }
            }

            listen("join") {
                val transaction = Sentry.startTransaction("minigame.queue_join", "queue.join")
                try {
                    val entry = retrieve<QueueEntry>("entry")
                    val kit = retrieve<String>("kit")
                    val queueType = retrieve<QueueType>("queueType")
                    val teamSize = retrieve<Int>("teamSize")

                    transaction.setData("leader", entry.leader.toString())
                    transaction.setData("kit", kit)
                    transaction.setData("queue_type", queueType.name)
                    transaction.setData("team_size", teamSize)
                    transaction.setData("party_size", entry.players.size)

                    val queueId = "$kit:${queueType.name}:${teamSize}v${teamSize}"
                    queueHolder.subscribe(queueId, entry)

                    transaction.status = SpanStatus.OK
                } catch (e: Exception) {
                    println("[queue] join FAILED: ${e.message}")
                    e.printStackTrace()
                    Sentry.captureException(e)
                    transaction.throwable = e
                    transaction.status = SpanStatus.INTERNAL_ERROR
                    throw e
                } finally {
                    transaction.finish()
                }
            }

            listen("leave") {
                val leader = retrieve<UUID>("leader")
                queueHolder.unsubscribe(leader)
            }
        }
    }

    private fun buildAndValidateQueueIndexes()
    {
        fun trackQueuesForKit(kit: ImmutableKit, providerVersion: MiniProviderVersion)
        {
            val sizeModels = kit
                .featureConfig(
                    FeatureFlag.QueueSizes,
                    key = "sizes"
                )
                .split(",")
                .map { sizeModel ->
                    val split = sizeModel.split(":")
                    split[0].toInt() to (split.getOrNull(1)
                        ?.split("+")
                        ?.map(QueueType::valueOf)
                        ?: listOf(QueueType.Casual))
                }

            QueueType.entries
                .forEach scope@{
                    for (model in sizeModels)
                    {
                        val queueId = queueId {
                            queueType(it)
                            kit(kit.id)
                            teamSize(model.first)
                        }

                        if (
                            it == QueueType.Ranked &&
                            (!kit.features(FeatureFlag.Ranked) || QueueType.Ranked !in model.second)
                        )
                        {
                            // a ranked queue exists for this kit, but the kit no longer supports ranked
                            queueHolder.forgetPlayerQueue(queueId)
                            return@scope
                        }

                        val queue = if (it != QueueType.Robot)
                        {
                            SubscribableDuelPlayerQueue(
                                kit = kit,
                                queueType = it,
                                teamSize = model.first,
                                providerVersion = providerVersion
                            )
                        } else
                        {
                            SubscribableSoloRobotPlayerQueue(kit, 1)
                        }

                        if (!queueHolder.isHolding(queueId))
                        {
                            queueHolder.trackPlayerQueue(queue)
                        }

                        if (queue is SubscribableSoloRobotPlayerQueue)
                        {
                            val additionalQueue = SubscribableDuoRobotPlayerQueue(kit, 2)
                            if (!queueHolder.isHolding(additionalQueue.id))
                            {
                                queueHolder.trackPlayerQueue(additionalQueue)
                            }
                        }
                    }
                }
        }

        KitDataSync.cached().kits.values
            .forEach { trackQueuesForKit(it, MiniProviderVersion.LEGACY) }
        KitDataSync.Modern.cached().kits.values
            .forEach { trackQueuesForKit(it, MiniProviderVersion.MODERN) }

        dpsQueueRedis.start()

        val bedwarsKitIDs = listOf(
            "bw_mini_solo" to BedWarsMode.Solo,
            "bw_mini_duos" to BedWarsMode.Duo,
            "bw_mega_quads" to BedWarsMode.Quads,
            "bw_mega_trios" to BedWarsMode.Trios,
            "bw_special_4v4" to BedWarsMode.Special4v4,
        )

        bedwarsKitIDs.forEach {
            val bedWarsKit = lookupKit(it.first)?.first
            if (bedWarsKit != null)
            {
                queueHolder.trackPlayerQueue(BedWarsSubscribableMinigamePlayerQueue(bedWarsKit, it.second))
            } else
            {
                queueHolder.forgetPlayerQueue(it.first)
            }
        }

        val mappings = listOf(
            "sumo_arcade" to ArcadeMode.SUMO,
            "oitc_arcade" to ArcadeMode.OITC,
            "rlgl_arcade" to ArcadeMode.RED_LIGHT_GREEN_LIGHT
        )

        mappings.forEach {
            val arcadeKit = lookupKit(it.first)?.first
            if (arcadeKit != null)
            {
                queueHolder.trackPlayerQueue(ArcadeSubscribableMinigamePlayerQueue(arcadeKit, it.second))
            } else
            {
                queueHolder.forgetPlayerQueue(it.first)
            }
        }

        val skywarsKitIDs = listOf(
            SkyWarsMode.MINI to "sw_mini"
        )

        skywarsKitIDs.forEach {
            val skyWarsKit = lookupKit(it.second)?.first
            if (skyWarsKit != null)
            {
                queueHolder.trackPlayerQueue(SkyWarsSubscribableMinigamePlayerQueue(skyWarsKit, it.first))
            } else
            {
                queueHolder.forgetPlayerQueue(it.second)
            }
        }

        val kitId = listOf(
            MiniWallsMode.SQUADS to "mw_main",
            MiniWallsMode.SOLO to "mw_main",
        )

        kitId.forEach { pair ->
            val miniWallsKit = lookupKit(pair.second)?.first
            if (miniWallsKit != null)
            {
                println("tracked mini walls queue")
                queueHolder.trackPlayerQueue(MiniWallsSubscribableMinigamePlayerQueue(miniWallsKit, pair.first))
            } else
            {
                queueHolder.forgetPlayerQueue(pair.second)
            }
        }

        val sgKitId = listOf(
            HungerGamesMode.SOLO_NORMAL to "sg_solo_normal",
        )

        /*sgKitId.forEach { pair ->
            val sgKit = lookupKit(pair.second)?.first
            if (sgKit != null)
            {
                println("tracked SG queue")
                queueHolder.trackPlayerQueue(HungerGamesSubscribableMinigamePlayerQueue(sgKit, pair.first))
            } else
            {
                queueHolder.forgetPlayerQueue(pair.second)
            }
        }*/

        sgKitId.forEach { pair ->
            queueHolder.forgetPlayerQueue(pair.second)
        }

        val pofKitId = listOf(
            PofMode.SOLO to "pof_main",
            PofMode.SOLO_LEGACY to "legacy_pof_main",
        )

        pofKitId.forEach { pair ->
            val pofKit = lookupKit(pair.second)?.first
            if (pofKit != null)
            {
                println("tracked pof queue (${pair.first})")
                queueHolder.trackPlayerQueue(PofSubscribableMinigamePlayerQueue(pofKit, pair.first))
            } else
            {
                queueHolder.forgetPlayerQueue(pair.second)
            }
        }

        // cleanup queues for kits that no longer exist
        queueHolder.playerQueues.forEach { (key, queue) ->
            val kitId = queue.toQueueIDComponents()?.kitID
                ?: return@forEach

            if (lookupKit(kitId) == null)
            {
                queueHolder.forgetPlayerQueue(key)
            }
        }
    }
}
