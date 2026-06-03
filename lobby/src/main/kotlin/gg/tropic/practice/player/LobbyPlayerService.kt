package gg.tropic.practice.player

import com.cryptomorin.xseries.XSound
import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.scala.basics.plugin.settings.defaults.values.StateSettingValue
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.commons.metadata.SpigotNetworkMetadataDataSync
import gg.scala.commons.playerstatus.PlayerStatusPreUpdateEvent
import gg.scala.commons.playerstatus.isVirtuallyInvisibleToSomeExtent
import gg.scala.commons.spatial.toPosition
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.handler.PlayerHandler
import gg.scala.queue.spigot.stream.SpigotRedisService
import gg.tropic.game.extensions.cosmetics.EquipOnLoginCosmeticService
import gg.tropic.practice.PracticeLobby
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.minigame.MinigameLobby
import gg.tropic.practice.configuration.minigame.MotionPreset
import gg.tropic.practice.configuration.minigame.teleportWithVelocityPreset
import gg.tropic.practice.parkour.extractPlaySession
import gg.tropic.practice.parkour.formatDurationIntoTwoDecimal
import gg.tropic.practice.parkour.isPlayingParkour
import gg.tropic.practice.player.prevention.PreventionListeners
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.quests.QuestsService
import gg.tropic.practice.quests.model.tracker.QuestTrackerState
import gg.tropic.practice.queue.QueueService
import gg.tropic.practice.queue.QueueType
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.cooldown.Cooldown
import me.lucko.helper.cooldown.CooldownMap
import me.lucko.helper.utils.Players
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.bukkit.Tasks
import net.evilblock.cubed.util.math.Numbers
import net.evilblock.cubed.util.time.TimeUtil
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityPortalEnterEvent
import org.bukkit.event.player.PlayerInitialSpawnEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.util.Vector
import org.spigotmc.event.player.PlayerSpawnLocationEvent
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Service
object LobbyPlayerService
{
    @Inject
    lateinit var plugin: PracticeLobby

    @Inject
    lateinit var audiences: BukkitAudiences

    private val playerCache = mutableMapOf<UUID, LobbyPlayer>()

    @Configure
    fun configure()
    {
        Events
            .subscribe(PlayerStatusPreUpdateEvent::class.java)
            .handler { event ->
                val description = when (true)
                {
                    MinigameLobby.isMainLobby() -> "Main"
                    MinigameLobby.isMinigameLobby() -> PracticeConfigurationService.minigameType().provide().displayName
                    else -> "Duels"
                }

                event.playerStatus.activityDescription = "in the $description Lobby"
                event.playerStatus.returnToServerGroup = "hub"
            }
            .bindWith(plugin)

        Bukkit.getWorlds()
            .forEach { world ->
                world.setGameRuleValue("doDaylightCycle", "false")
            }

        Schedulers
            .sync()
            .runRepeating({ _ ->
                Bukkit.getWorlds()
                    .forEach { world ->
                        world.time = 1000
                    }
            }, 0L, 20L)

        Schedulers
            .async()
            .runRepeating(Runnable {
                Players.all()
                    .mapNotNull(::find)
                    .onEach(LobbyPlayer::syncQueueStateIfRequired)
                    .onEach {
                        val player = it.player
                            ?: return@onEach

                        val audience = audiences.player(player)
                        if (player.isPlayingParkour())
                        {
                            val session = player.extractPlaySession()
                            audience.sendActionBar(
                                LegacyComponentSerializer.legacySection().deserialize(
                                    "${CC.AQUA}Parkour: ${CC.WHITE}${session.start.formatDurationIntoTwoDecimal()}s ${CC.GRAY}${Constants.THIN_VERTICAL_LINE} ${CC.AQUA}Checkpoints: ${CC.WHITE}0/0"
                                )
                            )
                        } else if (it.inQueue())
                        {
                            val shouldIncludePingRange = it.validateQueueEntry() &&
                                it.queueEntry().maxPingDiff != -1

                            val shouldIncludeELORange = it.validateQueueEntry() &&
                                it.queuedForType() == QueueType.Ranked

                            audience.sendActionBar(
                                "${CC.PRI}${it.queuedForType().name} ${it.queuedForTeamSize()}v${it.queuedForTeamSize()} ${
                                    it.queuedForKit()?.displayName ?: "???"
                                }${CC.WHITE}${
                                    if (shouldIncludeELORange && shouldIncludePingRange)
                                        "" else " ${CC.GRAY}${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}Queued for ${
                                        TimeUtil.formatIntoMMSS(
                                            (it.queuedForTime() / 1000).toInt()
                                        )
                                    }"
                                }${
                                    if (shouldIncludePingRange)
                                    {
                                        "${CC.GRAY} ${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}Ping: ${CC.BOLD}${
                                            it.queueEntry().leaderRangedPing
                                                .toIntRangeInclusive()
                                                .formattedDomain()
                                        }"
                                    } else
                                    {
                                        ""
                                    }
                                }${
                                    if (shouldIncludeELORange)
                                    {
                                        "${CC.GRAY} ${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}ELO: ${CC.BOLD}${
                                            it.queueEntry().leaderRangedELO
                                                .toIntRangeInclusive()
                                                .formattedDomain()
                                        }"
                                    } else
                                    {
                                        ""
                                    }
                                }".component
                            )
                        } else if (it.isNetworkQueue())
                        {
                            val queue = SpigotRedisService.findQueuePlayerIsIn(player)
                            if (queue != null)
                            {
                                audience.sendActionBar(
                                    Component.text(
                                        "${CC.PRI}${queue.displayName} Queue ${CC.D_GRAY}${
                                            Constants.THIN_VERTICAL_LINE
                                        } ${CC.GRAY}Your Position: ${CC.D_RED}${
                                            Numbers.format(
                                                queue.position(player.uniqueId)
                                            )
                                        }${CC.GRAY}/${CC.D_RED}${
                                            Numbers.format(
                                                queue.players.size
                                            )
                                        }"
                                    )
                                )
                            }
                        }
                    }
            }, 0L, 5L)

        Events
            .subscribe(PlayerSpawnLocationEvent::class.java)
            .handler {
                with(PracticeConfigurationService.cached()) {
                    it.spawnLocation = local().spawnLocation
                        .toLocation(
                            Bukkit.getWorlds().first()
                        )
                }
            }

        Events
            .subscribe(EntityPortalEnterEvent::class.java)
            .filter {
                it.entity is Player &&
                    !it.entity.hasMetadata("isPortalQueueing") &&
                    !(MinigameLobby.isMinigameLobby() || MinigameLobby.isMainLobby())
            }
            .handler {
                val player = it.entity as Player
                player.setMetadata(
                    "isPortalQueueing",
                    FixedMetadataValue(plugin, true)
                )

                Tasks.delayed(1L) {
                    find(player)?.findAndJoinRandomQueue()
                    player.removeMetadata("isPortalQueueing", plugin)
                }
            }
            .bindWith(plugin)

        EquipOnLoginCosmeticService.defaultFunctionality = false

        if (!SpigotNetworkMetadataDataSync.isFlagged("STRIPPED_LOBBY"))
        {
            plugin.logger.info { "skipping lobby prevention listeners" }
            Events
                .subscribe(PlayerToggleFlightEvent::class.java)
                .filter {
                    it.player.gameMode == GameMode.SURVIVAL &&
                        (MinigameLobby.isMinigameLobby() || MinigameLobby.isMainLobby())
                }
                .handler {
                    val vector = it.player.location
                        .direction
                        .multiply(
                            Vector(
                                0.85,
                                1.0,
                                0.85
                            )
                        )
                        .setY(1.1)

                    it.isCancelled = true
                    it.player.velocity = vector

                    XSound.ENTITY_FIREWORK_ROCKET_BLAST.play(it.player, 0.5f, 0.75f)
                }
        }


        Events
            .subscribe(
                PlayerJoinEvent::class.java,
                EventPriority.MONITOR
            )
            .handler { event ->
                val player = LobbyPlayer(event.player.uniqueId)
                playerCache[event.player.uniqueId] = player

                event.player.resetAttributes()

                event.player.inventory.heldItemSlot = 0
                event.player.updateInventory()

                val basicsProfile = BasicsProfileService.find(event.player)
                    ?: return@handler

                event.player.configureFlight()

                CompletableFuture.runAsync(player::syncQueueState)
                    .thenRun {
                        player.hasSyncedInitialQueueState = true
                    }
                    .thenRunAsync {
                        if (
                            MinigameLobby.isMinigameLobby() &&
                            QuestsService.isAutoAccepting(event.player)
                        )
                        {
                            val quests = QuestsService.getLimitedActiveMinigameQuests(
                                PracticeConfigurationService.minigameType()
                                    .provide()
                                    .internalId
                            )

                            quests
                                .filter { quest ->
                                    quest.toPlayerState(player.uniqueId) == QuestTrackerState.INACTIVE
                                }
                                .forEach { quest ->
                                    quest.updateState(player.uniqueId, QuestTrackerState.ACTIVE)
                                    event.player.sendMessage("${CC.GREEN}You automatically activated the ${CC.GOLD}${quest.name}${CC.GREEN} quest!")
                                }
                        }
                    }

                val profile = PracticeProfileService
                    .find(event.player)
                    ?: return@handler

                if (profile.hasActiveRankedBan())
                {
                    Tasks.delayed(20L) {
                        profile.deliverRankedBanMessage(event.player)
                    }
                }

                EquipOnLoginCosmeticService.callOnLoginFor(event.player)

                if (SpigotNetworkMetadataDataSync.isFlagged("STRIPPED_LOBBY"))
                {
                    return@handler
                }

                if (
                    basicsProfile.setting<StateSettingValue>(
                        "tropicprac:join-message",
                        StateSettingValue.ENABLED
                    ) == StateSettingValue.ENABLED &&
                    event.player.hasPermission("minigames.lobby-join-notifications") &&
                    !event.player.hasMetadata("disguised") &&
                    !event.player.isVirtuallyInvisibleToSomeExtent()
                )
                {
                    if (event.player.hasPermission("minigames.important-lobby-join-notifications"))
                    {
                        Bukkit.broadcastMessage(
                            "${CC.B_RED}>${CC.B_GOLD}>${CC.B_YELLOW}> ${
                                PlayerHandler.find(player.uniqueId)
                                    ?.getColoredName(prefixIncluded = true)
                                    ?: event.player.name
                            } ${CC.YELLOW}joined the lobby! ${CC.B_YELLOW}<${CC.B_GOLD}<${CC.B_RED}<"
                        )
                        return@handler
                    }

                    Bukkit.broadcastMessage(
                        "${CC.BD_AQUA}>${CC.B_AQUA}>${CC.B_GREEN}> ${
                            PlayerHandler.find(player.uniqueId)
                                ?.getColoredName(prefixIncluded = true)
                                ?: event.player.name
                        } ${CC.YELLOW}joined the lobby! ${CC.B_GREEN}<${CC.B_AQUA}<${CC.BD_AQUA}<"
                    )
                }
            }
            .bindWith(plugin)

        val playerLaunchpadCooldown = CooldownMap.create<UUID>(
            Cooldown.of(1000, TimeUnit.MILLISECONDS)
        )

        Events
            .subscribe(PlayerMoveEvent::class.java)
            .filter {
                it.player.location.block.type == Material.STONE_PLATE ||
                    it.player.location.block.type == Material.GOLD_PLATE
            }
            .handler {
                val configuration = PracticeConfigurationService.local()
                if (!playerLaunchpadCooldown.test(it.player.uniqueId))
                {
                    return@handler
                }

                val teleporterInZone = configuration.bezierTeleporters
                    .firstOrNull { zone ->
                        zone.start.upperRight
                            .toLocation(it.player.world)
                            .distance(it.player.location.block.location) < 6
                    }
                    ?: return@handler

                it.player.teleportWithVelocityPreset(
                    teleporterInZone.end.toLocation(it.player.world),
                    duration = teleporterInZone.duration,
                    height = teleporterInZone.height.toDouble(),
                    preset = teleporterInZone.preset
                )
            }
            .bindWith(plugin)

        Events
            .subscribe(
                PlayerQuitEvent::class.java,
                EventPriority.HIGHEST
            )
            .handler { event ->
                playerLaunchpadCooldown.reset(event.player.uniqueId)

                val profile = playerCache[event.player.uniqueId]
                    ?: return@handler

                if (profile.state == PlayerState.InQueue)
                {
                    QueueService.leaveQueue(event.player, true)
                }

                /*if (profile.state == PlayerState.InTournament)
                {
                    TournamentCommand.onLeave(
                        ScalaPlayer(event.player, audiences, plugin)
                    )
                }*/

                playerCache.remove(event.player.uniqueId)

            }
            .bindWith(plugin)
    }

    fun findBestAvailableLobby(): GameServer?
    {
        return ServerContainer
            .getServersInGroupCasted<GameServer>("hub")
            .filter {
                it.getWhitelisted() == ServerSync.getLocalGameServer().getWhitelisted()
            }
            .minByOrNull {
                it.getPlayersCount() ?: Int.MAX_VALUE // we don't want to send the player to a broken server >-<
            }
    }

    fun find(uniqueId: UUID) = playerCache[uniqueId]
    fun find(player: Player) = playerCache[player.uniqueId]
}

