package gg.tropic.practice.games

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XSound
import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.scala.basics.plugin.settings.defaults.values.StateSettingValue
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.spatial.Box
import gg.scala.lemon.handler.PlayerHandler
import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.game.extensions.cosmetics.CosmeticRegistry
import gg.tropic.game.extensions.cosmetics.victorydances.VictoryDance
import gg.tropic.game.extensions.cosmetics.victorydances.VictoryDanceCosmeticCategory
import gg.tropic.game.extensions.profile.CorePlayerProfileService
import gg.tropic.practice.expectation.ExpectationService
import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.feature.GameReportFeature
import gg.tropic.practice.games.bots.BotGameMetadata
import gg.tropic.practice.games.elo.ELOUpdates
import gg.tropic.practice.games.loadout.CustomLoadout
import gg.tropic.practice.games.loadout.DefaultLoadout
import gg.tropic.practice.games.loadout.SelectedLoadout
import gg.tropic.practice.games.ranked.PotPvPEloCalculator
import gg.tropic.practice.games.robot.RobotInstance
import gg.tropic.practice.games.tasks.GameStartTask
import gg.tropic.practice.games.tasks.GameStopTask
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.map.BuiltMapReplication
import gg.tropic.practice.map.MapReplicationService
import gg.tropic.practice.map.MapService
import gg.tropic.practice.map.metadata.impl.MapLevelMetadata
import gg.tropic.practice.map.metadata.impl.MapSpawnProtExpandMetadata
import gg.tropic.practice.minigame.MiniGameLifecycle
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.queue.QueueType
import gg.tropic.practice.region.Region
import gg.tropic.practice.serializable.Message
import gg.tropic.practice.statistics.*
import gg.tropic.practice.strategies.WorldEvictionStrategy
import gg.tropic.practice.extensions.BotMessages
import gg.tropic.practice.games.player.CosmeticPlayerResources
import gg.tropic.practice.map.instance.InstanceMap
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.bukkit.FancyMessage
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.text.TextUtil
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import okio.withLock
import org.apache.commons.lang.RandomStringUtils
import org.apache.commons.lang3.time.DurationFormatUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.util.Vector
import java.lang.AutoCloseable
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger
import kotlin.math.roundToInt

/**
 * @author GrowlyX
 * @since 8/4/2022
 */
open class GameImpl(
    val arenaWorld: World,
    expectation: GameExpectation,
    kit: Kit,
    teams: Set<GameTeam> = expectation.teams,
    var state: GameState = GameState.Waiting,
    private val mapId: String = expectation.mapId
) : AbstractGame<GameTeam>(expectation, teams, kit), CompositeTerminable
{
    private val backingTerminable = CompositeTerminable.create()
    var miniGameLifecycle: MiniGameLifecycle<*>? = null

    fun minigameType() = flagMetaData(FeatureFlag.MiniGameType, "id")

    @Transient
    var currentGameStartCountdown = 5

    private val snapshots = mutableMapOf<UUID, GameReportSnapshot>()

    val _map: gg.tropic.practice.map.Map
        get() = MapService.mapByAbsoluteID(mapId)!!

    val map = InstanceMap(
        map = _map,
        world = arenaWorld,
        compositeMeta = _map.metadata.composite(arenaWorld)
    )

    var replication: BuiltMapReplication? = null

    private val audiences: BukkitAudiences
        get() = ExpectationService.audiences

    private fun durationMillis() =
        System.currentTimeMillis() - this.startTimestamp

    private val defaultLoadout: DefaultLoadout = DefaultLoadout(kit)
    var botGameMetadata: BotGameMetadata? = null

    private val selectedKitLoadouts = mutableMapOf<UUID, SelectedLoadout>()
    private val playerCounters = mutableMapOf<UUID, LocalAccumulator>()

    val expectedSpectators = ConcurrentHashMap.newKeySet<UUID>()
    val expectedQueueRejoin = ConcurrentHashMap.newKeySet<UUID>()

    val placedBlocks = mutableSetOf<Vector>()
    val spawnProtectionZones = mutableListOf<Box>()
    val spawnProtectionZonesUnplaced = mutableListOf<Box>()
    val robotInstance = mutableSetOf<RobotInstance>()

    val consumedBots = AtomicInteger(0)
    var gameLifecycleObjectiveMet: (GameTeam) -> Boolean = { false }

    // TODO: Migrate to features
    var shouldContainIdentifiableTeams = true
    var shouldAllowFriendlyFire = false
    var shouldKeepCentralChat = false
    var shouldExplodeAll = false
    var shouldShowAllPlayers = false
    var shouldAllowCrafting = false
    var shouldBeMinMaxEligible = true
    var shouldBroadcastRespawnChatMsg = true
    var shouldSendHealthHUD = true
    var unplacedSpawnProtectionZone = false
    var voidDamageMin: Int? = null

    var isMinIndependent = false
    var isMinMaxLevelRange = false
    var minMaxLevelRange = 0..1

    val heartsBelowNametagTeam = RandomStringUtils.randomAlphanumeric(8)
    val heartsInTablistTeam = RandomStringUtils.randomAlphanumeric(8)

    val pendingLogins = ConcurrentHashMap<UUID, Long>()

    /**
     * Tracks players who have fully connected and fired [PlayerJoinEvent].
     * This is used by [MapReplicationService.startIfReady] to avoid starting the game
     * when [Bukkit.getPlayer] returns non-null during the login handshake but the player
     * hasn't actually loaded in yet (causing the "ghost player" / "0 ms" bug).
     */
    val readyPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /**
     * Becomes true when the game is detected to have been effectively solo —
     * i.e. one side had no actually-connected players at game start. When set,
     * [complete] skips ALL statistic updates (Plays, Wins/Losses, WinStreak,
     * ELO) and reward payouts. The goal is to never punish or reward a player
     * for a "duel" their opponent never showed up for.
     */
    @Volatile
    var invalidatedSolo: Boolean = false

    /**
     * Returns true only when every team has at least one currently-connected
     * Bukkit player. Used as a safeguard against a game entering or remaining
     * in the Playing state while one side is effectively empty.
     */
    fun eachTeamHasPresentPlayer(): Boolean = teams.all { team ->
        team.toBukkitPlayers().any { it != null }
    }

    fun loadout(player: Player) = selectedKitLoadouts[player.uniqueId] ?: defaultLoadout

    fun takeSnapshot(player: Player)
    {
        this.snapshots[player.uniqueId] =
            GameReportSnapshot(player, counter(player), kit)
    }

    fun takeSnapshotIfNotAlreadyExists(player: Player)
    {
        if (this.snapshots.containsKey(player.uniqueId))
        {
            return
        }

        takeSnapshot(player)
    }

    private var endTimestamp: Long = 0L

    fun lobbyGroup() = miniGameLifecycle?.typeConfiguration?.lobbyGroup ?: "miplobby"
    fun findBestAvailableLobby(): GameServer?
    {
        return ServerContainer
            .getServersInGroupCasted<GameServer>(miniGameLifecycle?.typeConfiguration?.lobbyGroup ?: "miplobby")
            .filter {
                it.getWhitelisted() == ServerSync.getLocalGameServer().getWhitelisted()
            }
            .minByOrNull {
                it.getPlayersCount() ?: Int.MAX_VALUE // we don't want to send the player to a broken server >-<
            }
    }

    private val playerResources = ConcurrentHashMap<UUID, CosmeticPlayerResources>()
    fun playerResourcesOf(player: Player) = playerResources.getOrPut(player.uniqueId) {
        CosmeticPlayerResources(
            player.uniqueId,
            player.name,
            PlayerHandler.find(player.uniqueId)
                ?.getColoredName(prefixIncluded = true)
                ?: player.name,
            player.hasMetadata("disguised")
        )
    }

    fun usernameOf(player: Player) = playerResourcesOf(player).username
    fun usernameOf(uniqueId: UUID) = playerResourcesOf(uniqueId)?.username ?: uniqueId.username()

    fun playerResourcesOf(player: UUID) = playerResources[player]

    val teamMutLock = ReentrantLock()
    fun attemptTeamSwap(player: Player, maxPlayers: Int, onTeamSwap: () -> Unit, newTeam: TeamIdentifier)
    {
        teamMutLock.withLock {
            val currentTeam = getTeamOf(player)
            if (newTeam == currentTeam.teamIdentifier)
            {
                player.sendMessage("${CC.RED}You are already on this team!")
                return@withLock
            }

            val availableSpace = teams
                .firstOrNull { it.teamIdentifier == newTeam }
                ?: return@withLock

            if (availableSpace.players.size >= maxPlayers)
            {
                player.sendMessage("${CC.RED}This team is full, or is allocated to a party!")
                return@withLock
            }

            currentTeam.players -= player.uniqueId
            availableSpace.players += player.uniqueId

            onTeamSwap()
        }
    }

    fun preWaitAdd(user: List<UUID>, teamID: TeamIdentifier)
    {
        val firstEmpty = teams.firstOrNull { it.teamIdentifier == teamID }
        firstEmpty?.players?.plusAssign(user)
        expectationModel.players += user
    }

    fun preWaitRemove(player: Player)
    {
        teamMutLock.withLock {
            expectationModel.players -= player.uniqueId
            getNullableTeam(player)?.players?.minusAssign(player.uniqueId)
        }
    }

    fun complete(winner: GameTeam?, reason: String = "")
    {
        if (state == GameState.Completed)
        {
            return
        }

        this.state = GameState.Completed

        var eloUpdates: CompletableFuture<ELOUpdates>? = null
        val positionUpdates = mutableMapOf<UUID, CompletableFuture<StatisticChange>>()
        val extraInformation = mutableMapOf<UUID, Map<String, Map<String, String>>>()
        val playerFeedback = mutableMapOf<UUID, MutableList<String>>()

        if (robot())
        {
            robotInstance.forEach(RobotInstance::destroy)
        }

        winner
            ?.toBukkitPlayers()
            ?.filterNotNull()
            ?.onEach {
                val victoryDance = CosmeticRegistry
                    .getAllEquipped(
                        VictoryDanceCosmeticCategory,
                        it
                    )
                    .randomOrNull()
                    ?: return@onEach

                (victoryDance as VictoryDance).applyTo(
                    toBukkitPlayers().filterNotNull(),
                    it,
                    null
                )
            }

        toBukkitPlayers()
            .filterNotNull()
            .forEach(::takeSnapshotIfNotAlreadyExists)

        endTimestamp = System.currentTimeMillis()

        if (winner == null)
        {
            this.report = GameReport(
                identifier = UUID.randomUUID(),
                winners = listOf(), losers = listOf(),
                snapshots = snapshots,
                duration = this.durationMillis(),
                resources = playerResources,
                kit = this.kit.displayName,
                map = this.mapId,
                status = GameReportStatus.ForcefullyClosed,
                extraInformation = extraInformation
            )
        } else
        {
            if (this.flag(FeatureFlag.FlyOnWin))
            {
                winner.nonSpectators()
                    .onEach {
                        it.allowFlight = true
                        it.isFlying = true
                    }
            }

            if (miniGameLifecycle == null && !invalidatedSolo)
            {
                this.toBukkitPlayers()
                    .filterNotNull()
                    .onEach {
                        val profile = CorePlayerProfileService.find(it)
                        if (profile != null && expectationModel.queueType != null)
                        {
                            val userIsWinner = winner.players.contains(it.uniqueId)
                            val queueMultiplier = expectationModel.queueType!!.coinMultiplier
                            if (queueMultiplier != 0.0)
                            {
                                profile.addXPRewardedBalance(
                                    amount = (queueMultiplier * (if (userIsWinner) 25L else 10L)).toLong(),
                                    currency = "coins",
                                    reason = if (userIsWinner) "Winning a game" else "Playing a game",
                                    feedback = { feedback ->
                                        playerFeedback.getOrPut(it.uniqueId) { mutableListOf() } += feedback
                                    }
                                )
                            }
                        }
                    }
            }

            val opponents = getAllOpponents(winner)
            val allOpponentPlayers = opponents.flatMap(GameTeam::players)
            // Skip ALL stat recording (plays/wins/losses/winstreak/ELO) when the game
            // was flagged as effectively solo — no opponent ever showed up, so there
            // is nothing to rank or streak against.
            if (!robot() && !expectationModel.isPrivateGame && expectationModel.queueType != null && !invalidatedSolo)
            {
                val trackedKitStatisticsUpdates = allOpponentPlayers.map {
                    StatisticService.update(it) {
                        statisticWrite(
                            statisticIds {
                                if (miniGameLifecycle != null)
                                {
                                    kits(minigameType())
                                } else
                                {
                                    globalKit()
                                }
                                kits(kit)
                                globalQueueType()
                                queueTypes(expectationModel.queueType!!)
                                daily()
                                weekly()
                                lifetime()
                                types(TrackedKitStatistic.Plays, TrackedKitStatistic.Losses)
                            }
                        ) {
                            add(1)
                        }

                        statisticWrite(
                            statisticIds {
                                kits(kit)
                                globalQueueType()
                                queueTypes(expectationModel.queueType!!)
                                lifetime()
                                lifetimes(StatisticLifetime.Daily)
                                types(TrackedKitStatistic.WinStreak)
                            }
                        ) {
                            update(0)
                        }
                    }.exceptionally { throwable ->
                        throwable.printStackTrace()
                        return@exceptionally null
                    }
                } + winner.players.map {
                    StatisticService.update(it) {
                        statisticWrite(
                            statisticIds {
                                if (miniGameLifecycle != null)
                                {
                                    kits(minigameType())
                                } else
                                {
                                    globalKit()
                                }
                                kits(kit)
                                queueTypes(expectationModel.queueType!!)
                                lifetime()
                                globalQueueType()
                                daily()
                                weekly()
                                types(TrackedKitStatistic.Plays, TrackedKitStatistic.Wins)
                            }
                        ) {
                            add(1)
                        }

                        statisticWrite(
                            statisticIds {
                                kits(kit)
                                globalQueueType()
                                queueTypes(expectationModel.queueType!!)
                                lifetime()
                                lifetimes(StatisticLifetime.Daily)
                                types(TrackedKitStatistic.WinStreak)
                            }
                        ) {
                            add(1)
                        }

                        val current = getStatisticValue(
                            statisticIdFrom(TrackedKitStatistic.WinStreak) {
                                kit(kit)
                                queueType(expectationModel.queueType!!)
                            }
                        ) ?: return@update

                        val highestWinStreakID = statisticIdFrom(TrackedKitStatistic.WinStreakHighest) {
                            kit(kit)
                            queueType(expectationModel.queueType!!)
                        }

                        val highestWinStreak = getStatisticValue(highestWinStreakID)
                            ?: return@update

                        if (highestWinStreak.score < current.score)
                        {
                            statisticWrite(highestWinStreakID) {
                                // This is in a lazily loaded clause, so we just add one to compensate.
                                update(current.score.toLong() + 1)
                            }
                        }
                    }.exceptionally { throwable ->
                        throwable.printStackTrace()
                        return@exceptionally null
                    }
                }

                val future = CompletableFuture.allOf(*trackedKitStatisticsUpdates.toTypedArray())
                val firstOpponent = opponents.first()
                if (expectationModel.queueType == QueueType.Ranked && isTwoTeamGame() && firstOpponent.players.size == 1)
                {
                    val winnerProfileID = winner.players.firstOrNull()
                    val loserProfileID = firstOpponent.players.firstOrNull()

                    if (winnerProfileID != null && loserProfileID != null)
                    {
                        eloUpdates = CompletableFuture()

                        positionUpdates[winnerProfileID] = CompletableFuture()
                        positionUpdates[loserProfileID] = CompletableFuture()

                        future.thenRunAsync {
                            val rankedEloID = statisticIdFrom(TrackedKitStatistic.ELO) {
                                kit(kit)
                                ranked()
                            }
                            val winnerCurrentELO = StatisticService
                                .get(winnerProfileID) {
                                    statisticRead(rankedEloID) {
                                        scoreAndPosition()
                                    }
                                }
                                .join()

                            val loserCurrentELO = StatisticService
                                .get(loserProfileID) {
                                    statisticRead(rankedEloID) {
                                        scoreAndPosition()
                                    }
                                }
                                .join()

                            val eloChanges = PotPvPEloCalculator.INSTANCE
                                .getNewRating(
                                    winnerCurrentELO?.score?.toInt() ?: 1000,
                                    loserCurrentELO?.score?.toInt() ?: 1000
                                )

                            val changeSetWinner = StatisticService
                                .update(winnerProfileID) {
                                    statisticWrite(rankedEloID) {
                                        add(eloChanges.winnerGain.toLong())
                                    }
                                }
                                .join()
                                .first()

                            val changeSetLoser = StatisticService
                                .update(loserProfileID) {
                                    statisticWrite(rankedEloID) {
                                        add(eloChanges.loserGain.toLong())
                                    }
                                }
                                .join()
                                .first()

                            val localELOUpdates = ELOUpdates(
                                winner = eloChanges.winnerNew to eloChanges.winnerGain,
                                loser = eloChanges.loserNew to eloChanges.loserGain
                            )

                            eloUpdates.complete(localELOUpdates)
                            positionUpdates[winnerProfileID]?.complete(changeSetWinner)
                            positionUpdates[loserProfileID]?.complete(changeSetLoser)
                        }
                    }
                }
            }

            toBukkitPlayers()
                .filterNotNull()
                .forEach {
                    counter(it).apply {
                        extraInformation[it.uniqueId] = mapOf(
                            "Hits" to mapOf(
                                "Total" to valueOf("totalHits").toInt().toString(),
                                "Max Combo" to valueOf("highestCombo").toInt().toString()
                            ),
                            "Other" to mapOf(
                                "Criticals" to valueOf("criticalHits").toInt().toString(),
                                "Blocked Hits" to valueOf("blockedHits").toInt().toString()
                            ),
                            "Health Regen" to mapOf(
                                "Regen" to "%.2f${Constants.HEART_SYMBOL}".format(
                                    valueOf("healthRegained").toFloat()
                                )
                            )
                        )
                    }
                }

            this.report = GameReport(
                identifier = UUID.randomUUID(),
                winners = winner.players.toList(),
                losers = allOpponentPlayers,
                snapshots = snapshots,
                duration = this.durationMillis(),
                map = this.mapId,
                kit = this.kit.displayName,
                status = GameReportStatus.Completed,
                resources = playerResources,
                extraInformation = extraInformation
            )

            if (robot())
            {
                val robot = robotInstance.firstOrNull() ?: return
                Schedulers
                    .async()
                    .runLater({
                        toBukkitPlayers()
                            .filterNotNull()
                            .forEach {
                                if (winner.players.contains(it.uniqueId))
                                {
                                    it.sendMessage(BotMessages.getBotLose(robot.name()))
                                } else
                                {
                                    if (it.uniqueId in allOpponentPlayers)
                                    {
                                        it.sendMessage(
                                            BotMessages.getBotWin(robot.name())
                                        )
                                    }
                                }

                                val profile = BasicsProfileService.find(it)
                                val messagingSoundsSettingValue = profile
                                    ?.setting(
                                        id = "messages_sounds",
                                        default = StateSettingValue.ENABLED
                                    )

                                if (messagingSoundsSettingValue == StateSettingValue.ENABLED)
                                {
                                    it.playSound(
                                        it.location,
                                        XSound.ENTITY_EXPERIENCE_ORB_PICKUP.parseSound(),
                                        1F, 1F
                                    )
                                }
                            }
                    }, 50L)
            }
        }

        GameReportFeature.saveSnapshotForAllParticipants(report!!, longTerm = miniGameLifecycle != null)

        val stopTask =
            GameStopTask(
                this, this.report!!, eloUpdates, positionUpdates,
                reason,
                playerFeedback = playerFeedback
            )

        stopTask.task = Schedulers.sync()
            .runRepeating(
                stopTask,
                0L, TimeUnit.SECONDS,
                1L, TimeUnit.SECONDS
            )

        stopTask.task.bindWith(this)
    }

    fun initializeTeamObjectives()
    {
        val minMax = map.metadata.metadata
            .filterIsInstance<MapLevelMetadata>()
            .sortedBy { metadata -> metadata.yAxis }
        if (minMax.size >= 2 && shouldBeMinMaxEligible)
        {
            val first = minMax.first().yAxis
            val last = minMax.last().yAxis
            isMinMaxLevelRange = true
            minMaxLevelRange = if (first > last)
            {
                last..first
            } else
            {
                first..last
            }
        }

        teams.forEach { team ->
            val identifier = team.teamIdentifier
            val spawnLocation = map.findSpawnLocationMatchingTeam(identifier)
            if (spawnLocation == null)
            {
                Logger.getGlobal().warning("[sentinel] There seems to be no spawn location for team ${identifier.label} on map ${map.name} ($mapId), for game $expectation.")
            }
        }

        val spawnProtectionExpandZone = map.metadata.metadata
            .filterIsInstance<MapSpawnProtExpandMetadata>()
            .firstOrNull()

        if (spawnProtectionExpandZone != null)
        {
            map.findSpawnLocations()
                .forEach { metadata ->
                    if (metadata.id == "spec")
                    {
                        return@forEach
                    }

                    if (unplacedSpawnProtectionZone)
                    {
                        spawnProtectionZonesUnplaced += spawnProtectionExpandZone.fromCenter(metadata.position)
                    } else
                    {
                        spawnProtectionZones += spawnProtectionExpandZone.fromCenter(metadata.position)
                    }
                }
        }

        if (flag(FeatureFlag.WinWhenNHitsReached))
        {
            gameLifecycleObjectiveMet = {
                val winWhenHitsReached = flagMetaData(
                    FeatureFlag.WinWhenNHitsReached, "hits"
                )
                    ?.toIntOrNull()

                winWhenHitsReached != null &&
                    it.gameLifecycleArbitraryObjectiveProgress >= winWhenHitsReached
            }
        } else
        {
            gameLifecycleObjectiveMet = {
                it.gameLifecycleArbitraryObjectiveProgress == 1
            }
        }
    }

    fun initializeAndStart()
    {
        initializeTeamObjectives()

        val startTask = GameStartTask(this)
        startTask.task = Schedulers.sync()
            .runRepeating(
                startTask,
                0L, TimeUnit.SECONDS,
                1L, TimeUnit.SECONDS
            )
        startTask.task.bindWith(this)
    }

    fun audiencesIndexed(lambda: (Audience, UUID) -> Unit) =
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                lambda(this.audiences.player(it), it.uniqueId)
            }

    fun audiences(lambda: (Audience) -> Unit) =
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                this.audiences.player(it).apply(lambda)
            }

    fun playSound(sound: Sound, pitch: Float = 1.0f)
    {
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                it.playSound(it.location, sound, 1.0f, pitch)
            }
    }

    fun allNonSpectators() = expectationModel.players
        .mapNotNull { Bukkit.getPlayer(it) }
        .filterNot { GameService.isSpectating(it) }

    fun sendCenteredMessage(vararg message: String) =
        sendMessage(*message.map { TextUtil.getCentered(it) }.toTypedArray())

    fun sendMessage(vararg message: String)
    {
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                message.forEach { msg ->
                    it.sendMessage(msg)
                }
            }

        this.expectedSpectators
            .mapNotNull(Bukkit::getPlayer)
            .forEach {
                message.forEach { msg ->
                    it.sendMessage(msg)
                }
            }
    }

    fun sendPersonalizedCenteredMessage(message: Player.() -> List<String>) =
        sendPersonalizedMessage {
            this.message().map { TextUtil.getCentered(it) }
        }

    fun sendPersonalizedMessage(message: Player.() -> List<String>)
    {
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                message(it).forEach { msg ->
                    it.sendMessage(msg)
                }
            }

        this.expectedSpectators
            .mapNotNull(Bukkit::getPlayer)
            .forEach {
                message(it).forEach { msg ->
                    it.sendMessage(msg)
                }
            }
    }

    fun sendMessage(vararg message: Message)
    {
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                message.forEach { msg ->
                    msg.sendToPlayer(it)
                }
            }

        this.expectedSpectators
            .mapNotNull(Bukkit::getPlayer)
            .forEach {
                message.forEach { msg ->
                    msg.sendToPlayer(it)
                }
            }
    }

    fun sendMessage(vararg message: FancyMessage)
    {
        this.toBukkitPlayers()
            .filterNotNull()
            .forEach {
                message.forEach { msg ->
                    msg.sendToPlayer(it)
                }
            }

        this.expectedSpectators
            .mapNotNull(Bukkit::getPlayer)
            .forEach {
                message.forEach { msg ->
                    msg.sendToPlayer(it)
                }
            }
    }

    fun getTeamOf(player: Player): GameTeam {
        val team = this.teams.firstOrNull { player.uniqueId in it.players }
        if (team != null) {
            return team
        }

        // Capture stack trace on main thread before going async
        val callStackTrace = Thread.currentThread().stackTrace
            .drop(2) // Skip getStackTrace and getTeamOf
            .take(10) // Top 10 frames
            .joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }

        // Report to Sentry async with structured data for filtering
        CompletableFuture.runAsync {
            io.sentry.Sentry.captureMessage("Player not in any team") { scope ->
                scope.level = io.sentry.SentryLevel.ERROR
                scope.setTag("alert_type", "player_no_team")
                scope.setTag("map_id", mapId)
                scope.setTag("kit_id", kit.id)
                scope.setTag("game_state", state.name)
                scope.setTag("queue_type", expectationModel.queueType?.name ?: "unknown")
                scope.setTag("is_minigame", (miniGameLifecycle != null).toString())

                // Extract caller info for quick filtering
                val caller = Thread.currentThread().stackTrace.getOrNull(4)
                scope.setTag("caller_class", caller?.className?.substringAfterLast('.') ?: "unknown")
                scope.setTag("caller_method", caller?.methodName ?: "unknown")

                scope.setExtra("player_name", player.name)
                scope.setExtra("player_uuid", player.uniqueId.toString())
                scope.setExtra("game_id", expectation.toString())
                scope.setExtra("expected_players", expectationModel.players.joinToString(",") { it.toString() })
                scope.setExtra("is_spectating", GameService.isSpectating(player).toString())
                scope.setExtra("is_expected_spectator", expectedSpectators.contains(player.uniqueId).toString())
                scope.setExtra("team_count", teams.size.toString())
                scope.setExtra("call_stack", callStackTrace)

                // Structured team data
                val teamData = teams.map { t ->
                    mapOf(
                        "id" to t.teamIdentifier.label,
                        "player_count" to t.players.size,
                        "players" to t.players.map { it.toString() }
                    )
                }
                scope.setExtra("teams", teamData.toString())
            }
        }

        throw IllegalArgumentException("Player ${player.name} was not in any team")
    }

    fun getTeamOf(player: UUID) = this.teams
        .firstOrNull {
            player in it.players
        }

    fun getNullableTeam(player: Player) = this.teams
        .firstOrNull {
            player.uniqueId in it.players
        }

    fun getNullableTeamOfID(id: UUID) = this.teams
        .firstOrNull {
            id in it.players
        }

    fun isTwoTeamGame() = teams.size == 2

    fun getOpponent(player: Player) =
        this.getOpponent(
            this.getTeamOf(player)
        )

    fun getOpponent(team: GameTeam) = if (team.teamIdentifier != TeamIdentifier.A)
        teams.first() else teams.last()

    fun getAllOpponents(team: GameTeam) = teams.filterNot { it.teamIdentifier == team.teamIdentifier }

    fun getDuration(): String
    {
        if (
            !this.ensurePlaying() &&
            !this.state(GameState.Completed)
        )
        {
            // This will return a capitalized name state which
            // makes sense in the context this function will be used in.
            return this.state.name
        }

        if (state(GameState.Completed))
        {
            return DurationFormatUtils.formatDuration(
                endTimestamp - startTimestamp,
                "mm:ss"
            )
        }

        return DurationFormatUtils.formatDuration(
            System.currentTimeMillis() - this.startTimestamp, "mm:ss"
        )
    }

    fun toBukkitPlayers() = expectationModel.players
        .map { player ->
            Bukkit.getPlayer(player)
        }

    fun toPlayers() = expectationModel.players

    fun generateRedirectMetadataFor(player: Player): Map<String, String>
    {
        if (
            player.uniqueId in expectedSpectators ||
            player.uniqueId !in expectedQueueRejoin
        )
        {
            if (expectationModel.players.size != 2)
            {
                if (player.uniqueId in expectationModel.players)
                {
                    return mapOf("was-game-participant" to "true")
                }

                return mapOf()
            }

            if (player.uniqueId in expectationModel.players)
            {
                val target = expectationModel.players
                    .firstOrNull { other ->
                        player.uniqueId != other
                    }
                    ?: return mapOf()

                if (expectationModel.queueId == "party" || expectationModel.queueId == "tournament")
                {
                    return mapOf("was-game-participant" to "true")
                }

                // This would expose the player's true name with the rematch item in the lobby
                if (playerResourcesOf(target)?.disguised == true)
                {
                    return mapOf("was-game-participant" to "true")
                }

                if (miniGameLifecycle != null)
                {
                    return mapOf("was-game-participant" to "true")
                }

                return mapOf(
                    "rematch-target-id" to target.toString(),
                    "rematch-kit-id" to expectationModel.kitId,
                    "rematch-region" to Region
                        .extractFrom(
                            ServerSync.getLocalGameServer().id
                        )
                        .name,
                    "rematch-map-id" to expectationModel.mapId,
                    "was-game-participant" to "true"
                )
            }

            return mapOf()
        }

        val queueType = expectationModel.queueType?.name
            ?: return mapOf()

        return mapOf(
            "requeue-kit-id" to expectationModel.kitId,
            "requeue-queue-type" to queueType,
            "was-game-participant" to "true"
        )
    }

    fun closeAndCleanup(kickPlayers: Boolean = true)
    {
        if (state != GameState.Completed)
        {
            Logger.getAnonymousLogger().info(
                "Game tried to close prematurely"
            )
            return
        }

        GameService.gameMappings.remove(this.expectation)

        robotInstance.forEach(RobotInstance::destroy)
        robotInstance.clear()

        if (replication != null)
        {
            MapReplicationService.writeReplications {
                this -= replication!!
            }
        }

        WorldEvictionStrategy.evictWorld(
            world = arenaWorld,
            redirectTarget = findBestAvailableLobby()?.id,
            kickPlayers = kickPlayers,
            generateRedirectMetadataFor = ::generateRedirectMetadataFor
        )

        closeAndReportException()
    }

    fun ensurePlaying() = this.state(GameState.Playing)

    fun state(state: GameState): Boolean
    {
        return this.state == state
    }

    fun flag(flag: FeatureFlag) = this.kit.features[flag] != null

    fun flagMetaData(flag: FeatureFlag, key: String): String?
    {
        if (!this.kit.features(flag))
        {
            return null
        }

        return this.kit
            .features[flag]
            ?.get(key)
            ?: flag.schema[key]
    }

    private val loadoutSelection = mutableMapOf<UUID, CompositeTerminable>()
    fun completeLoadoutSelection()
    {
        loadoutSelection.values.forEach(
            CompositeTerminable::closeAndReportException
        )
        loadoutSelection.clear()
    }

    fun enterLoadoutSelection(player: Player)
    {
        val profile = PracticeProfileService
            .find(player)
            ?: return run {
                defaultLoadout.apply(player)
            }

        val loadouts = profile.customLoadouts
            .getOrDefault(kit.id, listOf())

        if (loadouts.isEmpty())
        {
            defaultLoadout.apply(player)
            return
        }

        val defaultLoadoutID = UUID.randomUUID()

        val terminable = CompositeTerminable.create()
        terminable.with {
            if (!selectedKitLoadouts.containsKey(player.uniqueId))
            {
                defaultLoadout.apply(player)
                selectedKitLoadouts[player.uniqueId] = defaultLoadout
            }
        }

        val applicableLoadouts = loadouts
            .map {
                @Suppress("USELESS_CAST")
                CustomLoadout(it, kit) as SelectedLoadout
            }
            .associateBy {
                UUID.randomUUID().toString()
            }
            .toMutableMap()
            .apply {
                this[defaultLoadoutID.toString()] = defaultLoadout
            }

        val hashCodeToItemMappings = mutableMapOf<Int, String>()
        Events
            .subscribe(PlayerInteractEvent::class.java)
            .filter {
                it.player.uniqueId == player.uniqueId
            }
            .filter {
                it.action == Action.RIGHT_CLICK_AIR
            }
            .filter {
                hashCodeToItemMappings.containsKey(it.item.hashCode())
            }
            .handler {
                val loadout = applicableLoadouts[hashCodeToItemMappings[it.item.hashCode()]]
                    ?: defaultLoadout

                selectedKitLoadouts[player.uniqueId] = loadout

                terminable.closeAndReportException()
                loadoutSelection.remove(player.uniqueId)

                loadout.apply(player)

                player.sendMessage(
                    "${CC.GREEN}You have selected the ${CC.WHITE}${loadout.displayName()}${CC.GREEN} loadout!"
                )
            }
            .bindWith(terminable)

        applicableLoadouts.forEach { (t, u) ->
            val item = ItemBuilder
                .of(
                    if (t == defaultLoadoutID.toString())
                        XMaterial.BOOK else XMaterial.ENCHANTED_BOOK
                )
                .name("${CC.GREEN}${u.displayName()} ${CC.GRAY}(Right-Click)")
                .addToLore(
                    "${CC.GRAY}Click to select this loadout."
                )
                .build()

            hashCodeToItemMappings[item.hashCode()] = t
            player.inventory.addItem(item)
        }

        loadoutSelection[player.uniqueId] = terminable
    }

    fun lifecycle() = kit.lifecycle()
    fun deathDecisionLifecycle() = if (kit.lifecycle() == GameLifecycle.MiniGame)
        miniGameLifecycle?.configuration?.lifecycleType ?: GameLifecycle.SoulBound else kit.lifecycle()

    fun trackPendingLogin(player: UUID)
    {
        pendingLogins.put(player, System.currentTimeMillis())
    }

    fun counter(player: Player) = playerCounters.getOrPut(player.uniqueId) { LocalAccumulator(player.uniqueId) }
    open fun buildResources(): Boolean
    {
        Schedulers
            .async()
            .runRepeating({ _ ->
                pendingLogins.forEach { k, v ->
                    if (System.currentTimeMillis() - v > 5000L)
                    {
                        teamMutLock.withLock {
                            expectationModel.players -= k
                            getTeamOf(k)?.players?.remove(k)
                        }

                        pendingLogins.remove(k)
                        println("Player $k took too long to login to their Minigame instance (${minigameType()}, $expectation). They have been removed.")
                    }
                }
            }, 0L, 10L)
            .bindWith(this)

        toPlayers().forEach {
            playerCounters[it] = LocalAccumulator(it)
        }
        return true
    }

    fun cleanupWorld()
    {
        if (placedBlocks.isNotEmpty())
        {
            placedBlocks.forEach {
                arenaWorld
                    .getBlockAt(it.toLocation(arenaWorld))
                    .apply {
                        type = Material.AIR
                    }
            }
        }

        arenaWorld.entities
            .filterIsInstance<Item>()
            .onEach {
                it.remove()
            }

        arenaWorld.entities
            .filterIsInstance<org.bukkit.entity.EnderPearl>()
            .onEach {
                it.remove()
            }
    }

    fun buildLimit(): Int?
    {
        val buildLimit = flagMetaData(
            FeatureFlag.BuildLimit,
            key = "blocks"
        )?.toIntOrNull()
            ?: return null

        return (map.findSpawnLocationMatchingTeam(TeamIdentifier.A)?.y?.roundToInt() ?: 0) + buildLimit
    }

    fun robot(solaraID: UUID) = robotInstance.firstOrNull { it.solaraID() == solaraID }

    fun robot(): Boolean
    {
        return expectationModel.queueType == QueueType.Robot
    }

    fun humanSide() = teams.first()
    fun robotSide() = teams.last()

    override fun close()
    {
        backingTerminable.close()
    }

    override fun with(autoCloseable: AutoCloseable?): CompositeTerminable? =
        backingTerminable.with(autoCloseable)

    override fun cleanup()
    {
        backingTerminable.cleanup()
    }
}
