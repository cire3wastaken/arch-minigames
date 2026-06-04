package gg.tropic.practice.minigame

import com.cryptomorin.xseries.XMaterial
import gg.scala.basics.plugin.settings.SettingMenu
import gg.scala.commons.ScalaCommons
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.friends.menu.template.impl.ProfileSinglePageMenu
import gg.scala.lemon.hotbar.HotbarPreset
import gg.scala.lemon.hotbar.HotbarPresetHandler
import gg.scala.lemon.hotbar.entry.impl.DynamicHotbarPresetEntry
import gg.scala.lemon.hotbar.entry.impl.StaticHotbarPresetEntry
import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.game.extensions.cosmetics.CosmeticCategoryMenu
import gg.tropic.practice.PracticeLobby
import gg.tropic.practice.category.visibility.SpawnPlayerVisibility
import gg.tropic.practice.commands.*
import gg.tropic.practice.configuration.MinigameLobbyConfiguration
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.extensions.unbreakable
import gg.tropic.practice.minigame.hologram.MinigameLeaderboardHologramEntity
import gg.tropic.practice.minigame.holographicstats.CoreHolographicStatsHologramEntity
import gg.tropic.practice.minigame.levitationportals.LevitationPortal
import gg.tropic.practice.minigame.npc.MinigameLobbyNPCEntity
import gg.tropic.practice.minigame.npc.MinigamePlayNPCEntity
import gg.tropic.practice.minigame.quests.QuestMasterEntity
import gg.tropic.practice.minigame.remove.TrackedLobbyInstance
import gg.tropic.practice.minigame.top3.MinigameTop3HologramEntity
import gg.tropic.practice.minigame.top3.MinigameTop3NPCEntity
import gg.tropic.practice.player.LobbyPlayerService
import gg.tropic.practice.player.LobbyPlayerService.findBestAvailableLobby
import gg.tropic.practice.player.PlayerState
import gg.tropic.practice.player.hotbar.LobbyHotbarService
import gg.tropic.practice.replacements.toTemplatePlayerCounts
import gg.tropic.practice.services.LeaderboardManagerService
import gg.tropic.practice.services.NetworkFluidityService
import me.lucko.helper.Events
import me.lucko.helper.Helper
import me.lucko.helper.Schedulers
import me.lucko.helper.cooldown.Cooldown
import me.lucko.helper.cooldown.CooldownMap
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.ScalaCommonsSpigot
import net.evilblock.cubed.entity.EntityHandler
import net.evilblock.cubed.entity.hologram.HologramEntity
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.buttons.TexturedHeadButton
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.bukkit.Tasks
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPortalEnterEvent
import org.bukkit.metadata.FixedMetadataValue
import java.time.Duration
import java.util.*

/**
 * @author Subham
 * @since 6/26/25
 */
@Service
object MinigameLobby
{
    @Inject
    lateinit var plugin: PracticeLobby

    private var customizer: MinigameLobbyCustomizer? = null
    private var isMainLobby = false

    fun customizer() = customizer!!
    fun isMinigameLobby() = customizer != null
    fun isMainLobby() = isMainLobby

    var competitiveResolver: ((Player) -> MinigameCompetitiveCustomizer?)? = null
    var holographicStatsEnabled: () -> Boolean = { customizer is MinigameCompetitiveCustomizer }

    fun competitiveFor(player: Player): MinigameCompetitiveCustomizer? =
        competitiveResolver?.invoke(player) ?: (customizer as? MinigameCompetitiveCustomizer)

    var globalPlayerCount = 0
    var lobbies = mapOf<String, TrackedLobbyInstance>()

    @Configure
    fun configure()
    {
        configureLobbyNPCs()
        configurePortals()
        configureLobbyNPCUpdateSystem()

        PracticeConfigurationService.onReload {
            configureLobbyNPCs()
            configurePortals()
        }
    }

    fun customizeMainLobby()
    {
        isMainLobby = true

        Schedulers
            .async()
            .runRepeating(Runnable {
                globalPlayerCount = ScalaCommonsSpigot
                    .instance.kvConnection
                    .sync().hgetall(
                        "symphony:instances"
                    )
                    .filter { pair ->
                        System.currentTimeMillis() - (ScalaCommons.bundle().globals().redis()
                            .sync()
                            .hget("symphony:heartbeats", pair.key)
                            ?.toLongOrNull() ?: 0) < Duration
                            .ofSeconds(5L)
                            .toMillis()
                    }
                    .values
                    .sumOf { it.toIntOrNull() ?: 0 }

                val servers = ServerContainer.getServersInGroupCasted<GameServer>("hub")

                lobbies = servers
                    .sortedBy { it.id }
                    .mapIndexed { index, gameServer ->
                        TrackedLobbyInstance(gameServer = gameServer, friendlyId = index + 1)
                    }
                    .associateBy { it.gameServer.id }
            }, 0L, 10L)

        Schedulers
            .async()
            .runRepeating(Runnable {
                EntityHandler.getEntitiesByType(HologramEntity::class.java)
                    .filter {
                        !it.getLines().any { text -> text.contains("Parkour", true) }
                    }
                    .forEach {
                        it.updateLines(
                            it.getLines().map { text ->
                                text.toTemplatePlayerCounts()
                            }
                        )
                    }
            }, 0L, 10L)

        LobbyHotbarService.reconfigureForMinigames()
        NetworkFluidityService.disableFluidityPartially()
        LobbyHotbarService.set(
            PlayerState.Idle,
            generateMainLobbyHotbar()
        )

        plugin.unregisterCommands(
            StatResetTokenCommand,
            ToggleSpectatorsCommand,
            PlayCommand,
            QueueMenuCommands,
            LeaderboardsCommand,
            KitEditorCommand,
            GetLobbyItemsCommand,
            DuelRequestsCommand,
            StatisticsCommand
        )

        SettingMenu.defaultCategory = "Minigames"
    }

    fun customize(
        typeProvider: MiniGameTypeProvider,
        customizer: MinigameLobbyCustomizer
    )
    {
        this.customizer = customizer

        NetworkFluidityService.disableFluidity()
        PracticeConfigurationService.registerTypeProvider(typeProvider)
        LobbyHotbarService.set(
            PlayerState.Idle, generateDefaultHotbar(
                typeProvider, customizer
            )
        )

        configurePlayNPCs()
        PracticeConfigurationService.onReload {
            configurePlayNPCs()
        }

        // for some reason this wasn't always
        // running, so do it again! (issue with provider?)
        configureLobbyNPCs()

        plugin.commandManager.registerCommand(QuestsCommand)
        plugin.unregisterCommands(
            StatResetTokenCommand,
            ToggleSpectatorsCommand,
            ToggleAutoAcceptQuestsCommand,
            PlayCommand,
            QueueMenuCommands,
            LeaderboardsCommand,
            KitEditorCommand,
            GetLobbyItemsCommand,
            DuelRequestsCommand
        )

        Schedulers
            .async()
            .runRepeating({ _ ->
                EntityHandler
                    .getEntitiesByType(MinigamePlayNPCEntity::class.java)
                    .forEach { entity ->
                        if (entity.modeMetadata == null)
                        {
                            if (entity.isAutoJoin)
                            {
                                entity.updateLines(entity.generateAutoJoinLines())
                            }
                            return@forEach
                        }

                        entity.updateLines(entity.currentHeader())
                    }
            }, 0L, 10L)

        Schedulers
            .async()
            .runRepeating({ _ ->
                reconfigureTop3()
            }, 0L, 20L * 60L * 3L)

        Events
            .subscribe(EntityPortalEnterEvent::class.java)
            .filter { it.entity is Player }
            .handler { event ->
                val nearbyPlayNPCEntity = EntityHandler
                    .getEntitiesOfType<MinigamePlayNPCEntity>()
                    .firstOrNull { entity ->
                        entity.location.world == event.location.world &&
                            entity.location.distance(event.location) < 4.0
                    }
                    ?: return@handler run {
                        val bestLobby = findBestAvailableLobby()
                            ?: return@run

                        VelocityRedirectSystem.redirect(
                            event.entity as Player, bestLobby.id
                        )
                    }

                nearbyPlayNPCEntity.onPortal(event.entity as Player)
            }

        LobbyHotbarService.reconfigureForMinigames()

        with(PracticeConfigurationService.cached()) {
            if (!minigameConfigurations.containsKey(typeProvider.provide().internalId))
            {
                minigameConfigurations[typeProvider.provide().internalId] = MinigameLobbyConfiguration()
                PracticeConfigurationService.sync(this)
            }
        }

        SettingMenu.defaultCategory = "Minigames"
    }

    fun generateDefaultHotbar(
        typeProvider: MiniGameTypeProvider,
        customizer: MinigameLobbyCustomizer
    ): HotbarPreset
    {
        val preset = HotbarPreset()
        preset.addSlot(
            0,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.COMPASS)
                    .name("${CC.GREEN}Play ${typeProvider.provide().displayName} ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    customizer.playProvider(player)
                }
            }
        )

        preset.addSlot(
            1,
            StaticHotbarPresetEntry(
                ItemBuilder(typeProvider.provide().item)
                    .name("${CC.GREEN}${typeProvider.provide().displayName} Menu ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    customizer.mainMenuProvider(player)
                }
            }
        )

        preset.addSlot(
            6,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    return@scope ItemBuilder
                        .of(XMaterial.CLOCK)
                        .name("${CC.GREEN}Game Selector ${CC.GRAY}(Right Click)")
                        .build()
                }

                it.onClick = scope@{ player ->
                    PracticeConfigurationService.openEditableUI(player, "game_selector")
                }
            }
        )

        preset.addSlot(
            2,
            DynamicHotbarPresetEntry().apply {
                onBuild = scope@{ player ->
                    ItemBuilder
                        .copyOf(
                            object :
                                TexturedHeadButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVlMzEzZTMwYzUzZGUxNzZlN2YzY2ZjYzI3ODI3ZmQ0NWUxN2QwYzRiOTljNmMxZmI1MmE3MGFiMjkzOTMyNCJ9fX0=")
                            {}
                                .getButtonItem(player)
                        )
                        .name("${CC.GREEN}Create a Party ${CC.GRAY}(Right Click)")
                        .build()
                }

                onClick = scope@{ player ->
                    player.performCommand("party create")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.InPartyAsLeader
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        preset.addSlot(
            4,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.CHEST)
                    .name("${CC.GREEN}Cosmetics ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    CosmeticCategoryMenu().openMenu(player)
                }
            }
        )

        val visibilityCooldown = CooldownMap.create<UUID>(Cooldown.ofTicks(20L))
        preset.addSlot(
            7,
            DynamicHotbarPresetEntry().apply {
                onBuild = context@{
                    if (SpawnPlayerVisibility.get(it))
                    {
                        return@context ItemBuilder
                            .of(XMaterial.LIME_DYE)
                            .name("${CC.GRAY}Players: ${CC.GREEN}Enabled ${CC.WHITE}(Right Click)")
                            .build()
                    }

                    return@context ItemBuilder
                        .of(XMaterial.GRAY_DYE)
                        .name("${CC.GRAY}Players: ${CC.RED}Disabled ${CC.GRAY}(Right Click)")
                        .build()
                }

                onClick = context@{
                    if (!visibilityCooldown.test(it.uniqueId))
                    {
                        it.sendMessage("${CC.RED}Slow down! You are toggling visibility too fast!")
                        return@context
                    }

                    TogglePlayerVisibilityCommand.onToggleVisibility(it)
                    preset.applyToPlayer(it)
                }
            }
        )

        preset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.NETHER_STAR)
                    .name("${CC.GREEN}Switch ${typeProvider.provide().displayName} Lobby ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    SwitchLobbyServerCommand.SwitchLobbyServerMenu().openMenu(player)
                }
            }
        )

        HotbarPresetHandler.startTrackingHotbar("idle-minigames", preset)
        return preset
    }


    fun generateMainLobbyHotbar(): HotbarPreset
    {
        val preset = HotbarPreset()
        preset.addSlot(
            0,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.COMPASS)
                    .name("${CC.GREEN}Game Selector ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    Button.playNeutral(player)
                    PracticeConfigurationService.openEditableUI(player, "game_selector")
                }
            }
        )

        preset.addSlot(
            1,
            DynamicHotbarPresetEntry().also {
                it.onBuild = { player ->
                    ItemBuilder
                        .of(XMaterial.SKELETON_SKULL)
                        .data(3.toShort())
                        .name("${CC.GREEN}Profile Menu ${CC.GRAY}(Right Click)")
                        .owner(player.name)
                        .unbreakable()
                        .build()
                }

                it.onClick = context@{ player ->
                    Button.playNeutral(player)
                    ProfileSinglePageMenu().openMenu(player)
                }
            }
        )

        preset.addSlot(
            2,
            DynamicHotbarPresetEntry().apply {
                onBuild = scope@{ player ->
                    ItemBuilder
                        .copyOf(
                            object :
                                TexturedHeadButton("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVlMzEzZTMwYzUzZGUxNzZlN2YzY2ZjYzI3ODI3ZmQ0NWUxN2QwYzRiOTljNmMxZmI1MmE3MGFiMjkzOTMyNCJ9fX0=")
                            {}
                                .getButtonItem(player)
                        )
                        .name("${CC.GREEN}Create a Party ${CC.GRAY}(Right Click)")
                        .build()
                }

                onClick = scope@{ player ->
                    player.performCommand("party create")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.InPartyAsLeader
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        preset.addSlot(
            4,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.CHEST)
                    .name("${CC.GREEN}Cosmetics ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    Button.playNeutral(player)
                    CosmeticCategoryMenu().openMenu(player)
                }
            }
        )

        val visibilityCooldown = CooldownMap.create<UUID>(Cooldown.ofTicks(20L))
        preset.addSlot(
            7,
            DynamicHotbarPresetEntry().apply {
                onBuild = context@{
                    if (SpawnPlayerVisibility.get(it))
                    {
                        return@context ItemBuilder
                            .of(XMaterial.LIME_DYE)
                            .name("${CC.GRAY}Players: ${CC.GREEN}Enabled ${CC.GRAY}(Right Click)")
                            .build()
                    }

                    return@context ItemBuilder
                        .of(XMaterial.GRAY_DYE)
                        .name("${CC.GRAY}Players: ${CC.RED}Disabled ${CC.GRAY}(Right Click)")
                        .build()
                }

                onClick = context@{
                    if (!visibilityCooldown.test(it.uniqueId))
                    {
                        it.sendMessage("${CC.RED}Slow down! You are toggling visibility too fast!")
                        return@context
                    }

                    Button.playNeutral(it)
                    TogglePlayerVisibilityCommand.onToggleVisibility(it)
                    preset.applyToPlayer(it)
                }
            }
        )

        preset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.NETHER_STAR)
                    .name("${CC.GREEN}Switch Main Lobby ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    Button.playNeutral(player)
                    SwitchLobbyServerCommand.SwitchLobbyServerMenu().openMenu(player)
                }
            }
        )

        HotbarPresetHandler.startTrackingHotbar("idle-mainlobby", preset)
        return preset
    }

    private fun configureLobbyNPCUpdateSystem()
    {
        Schedulers
            .async()
            .runRepeating({ _ ->
                EntityHandler
                    .getEntitiesByType(MinigameLobbyNPCEntity::class.java)
                    .forEach { entity ->
                        entity.updateLines(entity.generateLines())
                    }
            }, 0L, 10L)
    }

    private var portalTerminable = CompositeTerminable.create()
    private fun configurePortals()
    {
        portalTerminable.closeAndReportException()
        portalTerminable = CompositeTerminable.create()
        PracticeConfigurationService.local().levitationPortals
            .forEach { portal ->
                LevitationPortal(
                    spec = portal,
                    portalTeleport = {
                        it.sendMessage("todo: teleport")
                    },
                    portalEnter = {
                        it.sendMessage("todo: enter")
                    },
                    portalExit = {
                        it.sendMessage("todo: exit")
                    }
                ).subscribe(portalTerminable)
            }
    }

    private fun configureLobbyNPCs()
    {
        EntityHandler
            .getEntitiesByType(MinigameLobbyNPCEntity::class.java)
            .forEach { entity ->
                entity.destroyForCurrentWatchers()
                EntityHandler.forgetEntity(entity)
            }

        PracticeConfigurationService.local().minigameLobbyNPCs
            .forEach { playNPC ->
                println("Spawning play npc: ${playNPC.gamemodeName}")
                MinigameLobbyNPCEntity(playNPC).configure()
            }
    }

    private fun configurePlayNPCs()
    {
        listOf(
            MinigamePlayNPCEntity::class.java,
            MinigameLeaderboardHologramEntity::class.java,
            MinigameTop3HologramEntity::class.java,
            QuestMasterEntity::class.java,
            CoreHolographicStatsHologramEntity::class.java
        ).forEach {
            EntityHandler
                .getEntitiesByType(it)
                .forEach { entity ->
                    entity.destroyForCurrentWatchers()
                    EntityHandler.forgetEntity(entity)
                }
        }

        if (holographicStatsEnabled())
        {
            CoreHolographicStatsHologramEntity(
                PracticeConfigurationService.local().coreHolographicStatsPosition
            ).apply {
                initializeData()
                EntityHandler.trackEntity(this)
            }
        }

        PracticeConfigurationService.local().playNPCs
            .forEach { playNPC ->
                MinigamePlayNPCEntity(playNPC).configure()
            }

        PracticeConfigurationService.local().leaderboards
            .forEach { hologram ->
                MinigameLeaderboardHologramEntity(hologram).configure()
            }

        PracticeConfigurationService.local().topPlayerNPCSets
            .forEach { hologram ->
                MinigameTop3HologramEntity(hologram).configure()
            }

        PracticeConfigurationService.local().questMasterLocation.apply {
            val entity = QuestMasterEntity(toLocation(
                Bukkit.getWorlds().first()
            ))

            entity.initializeData()
            EntityHandler.trackEntity(entity)

            entity.updateLines(listOf(
                "${CC.RED}Quest Master",
                "${CC.B_YELLOW}RIGHT CLICK"
            ))
        }

        reconfigureTop3()
    }

    fun reconfigureTop3()
    {
        EntityHandler
            .getEntitiesByType(MinigameTop3NPCEntity::class.java)
            .forEach { entity ->
                entity.destroyForCurrentWatchers()
                EntityHandler.forgetEntity(entity)
            }

        PracticeConfigurationService.local().topPlayerNPCSets
            .forEach { hologram ->
                val topPlayers = LeaderboardManagerService
                    .getCachedLeaderboards(hologram.statisticID)
                    ?: return@forEach run {
                        MinigameTop3NPCEntity(
                            "1st", "0 ${hologram.statisticDisplayName}s", null,
                            hologram.first.toLocation(Bukkit.getWorlds().first())
                        ).configure()

                        MinigameTop3NPCEntity(
                            "2nd", "0 ${hologram.statisticDisplayName}s", null,
                            hologram.second.toLocation(Bukkit.getWorlds().first())
                        ).configure()

                        MinigameTop3NPCEntity(
                            "3rd", "0 ${hologram.statisticDisplayName}s", null,
                            hologram.third.toLocation(Bukkit.getWorlds().first())
                        ).configure()
                    }

                topPlayers.getOrNull(0)
                    ?.apply {
                        MinigameTop3NPCEntity(
                            "1st",
                            "$value ${hologram.statisticDisplayName}${if (value == 1L) "" else "s"}",
                            uniqueId.username(),
                            hologram.first.toLocation(Bukkit.getWorlds().first())
                        ).configure()
                    }
                    ?: MinigameTop3NPCEntity(
                        "1st", "0 ${hologram.statisticDisplayName}s", null,
                        hologram.first.toLocation(Bukkit.getWorlds().first())
                    ).configure()

                topPlayers.getOrNull(1)
                    ?.apply {
                        MinigameTop3NPCEntity(
                            "2nd",
                            "$value ${hologram.statisticDisplayName}${if (value == 1L) "" else "s"}",
                            uniqueId.username(),
                            hologram.second.toLocation(Bukkit.getWorlds().first())
                        ).configure()
                    }
                    ?: MinigameTop3NPCEntity(
                        "2nd", "0 ${hologram.statisticDisplayName}s", null,
                        hologram.second.toLocation(Bukkit.getWorlds().first())
                    ).configure()

                topPlayers.getOrNull(2)
                    ?.apply {
                        MinigameTop3NPCEntity(
                            "3rd",
                            "$value ${hologram.statisticDisplayName}${if (value == 1L) "" else "s"}",
                            uniqueId.username(),
                            hologram.third.toLocation(Bukkit.getWorlds().first())
                        ).configure()
                    }
                    ?: MinigameTop3NPCEntity(
                        "3rd", "0 ${hologram.statisticDisplayName}s", null,
                        hologram.third.toLocation(Bukkit.getWorlds().first())
                    ).configure()
            }
    }

    fun configureRightClickDuel()
    {

    }
}
