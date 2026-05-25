package gg.tropic.practice.player.hotbar

import com.cryptomorin.xseries.XMaterial
import gg.scala.basics.plugin.settings.SettingMenu
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.metadata.SpigotNetworkMetadataDataSync
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.hotbar.HotbarPreset
import gg.scala.lemon.hotbar.HotbarPresetHandler
import gg.scala.lemon.hotbar.entry.impl.DynamicHotbarPresetEntry
import gg.scala.lemon.hotbar.entry.impl.StaticHotbarPresetEntry
import gg.scala.lemon.redirection.expectation.PlayerJoinWithExpectationEvent
import gg.scala.lemon.util.QuickAccess.username
import gg.scala.queue.spigot.command.LeaveQueueCommand
import gg.tropic.practice.Globals
import gg.tropic.practice.PracticeLobby
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.extensions.unbreakable
import gg.tropic.practice.isModernDuelsServer
import gg.tropic.practice.kit.KitService
import gg.tropic.practice.menu.MultiplexingDuelsSelectionMenu
import gg.tropic.practice.menu.JoinQueueMenu
import gg.tropic.practice.menu.LeaderboardsMenu
import gg.tropic.practice.menu.PlayerMainMenu
import gg.tropic.practice.menu.editor.EditorKitSelectionMenu
import gg.tropic.practice.menu.party.PartyPlayGameSelectMenu
import gg.tropic.practice.minigame.MinigameLobby
import gg.tropic.practice.player.LobbyPlayerService
import gg.tropic.practice.player.PlayerState
import gg.tropic.practice.player.player
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.queue.QueueService
import gg.tropic.practice.queue.QueueType
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIdFrom
import mc.arch.minigames.parties.model.PartySetting
import mc.arch.minigames.parties.service.NetworkPartyService
import mc.arch.minigames.parties.service.event.PartyCreateEvent
import mc.arch.minigames.parties.service.event.PartyUpdateEvent
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.menu.buttons.AddButton
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.bukkit.Tasks
import net.evilblock.cubed.util.bukkit.prompt.InputPrompt
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.*

@Service
object LobbyHotbarService
{
    @Inject
    lateinit var plugin: PracticeLobby

    @Inject
    lateinit var audiences: BukkitAudiences

    var buildNewBotFightMenu: (Player) -> Menu? = { null }
    var buildBotSelectorMenu: (Player) -> Menu? = { null }

    private val hotbarCache = mutableMapOf<PlayerState, HotbarPreset>()

    fun openRankedQueueMenu(player: Player)
    {
        val profile = PracticeProfileService.find(player)
            ?: return

        val configuration = PracticeConfigurationService.cached()
        if (!player.isOp && !configuration.rankedQueueEnabled)
        {
            player.sendMessage("${CC.RED}Ranked queues are temporarily disabled. Please try again later.")
            return
        }

        if (player.hasMetadata("frozen"))
        {
            player.sendMessage("${CC.RED}You cannot join queues while frozen!")
            return
        }

        Schedulers
            .async()
            .run {
                val globalWins = profile
                    .getStatisticValue(
                        statisticIdFrom(TrackedKitStatistic.Wins)
                    )
                    ?.score
                    ?.toLong()
                    ?: 0

                if (
                    !player.hasPermission("practice.bypass-ranked-queue-requirements")
                    && globalWins < 10
                )
                {
                    player.sendMessage(
                        "${CC.RED}You must have at least 10 wins to queue for a Ranked kit! You currently have ${CC.YELLOW}${
                            if (globalWins == 0L) "no wins" else "$globalWins win${
                                if (globalWins == 1L) "" else "s"
                            }"
                        }${CC.RED}."
                    )
                    return@run
                }

                if (profile.hasActiveRankedBan())
                {
                    profile.deliverRankedBanMessage(player)
                    return@run
                }

                openQueueMenuWithFormatSelect(player, QueueType.Ranked, 1)
            }
    }

    fun openQueueMenuWithFormatSelect(player: Player, queueType: QueueType, teamSize: Int)
    {
        if (!isModernDuelsServer())
        {
            JoinQueueMenu(player, queueType, teamSize).openMenu(player)
            return
        }

        MultiplexingDuelsSelectionMenu { modern ->
            JoinQueueMenu(player, queueType, teamSize, modernMode = modern).openMenu(player)
        }.openMenu(player)
    }

    @Configure
    fun configure()
    {
        val idlePreset = HotbarPreset()

        val wasGameParticipant = mutableSetOf<UUID>()
        val loginTasks = mutableMapOf<UUID, MutableList<(Player) -> Unit>>()

        Events
            .subscribe(PlayerQuitEvent::class.java)
            .handler {
                wasGameParticipant.remove(it.player.uniqueId)
                loginTasks.remove(it.player.uniqueId)
            }
            .bindWith(plugin)

        Events
            .subscribe(PartyCreateEvent::class.java)
            .handler { event ->
                val lobbyPlayer = LobbyPlayerService.find(event.player)
                    ?: return@handler

                synchronized(lobbyPlayer.stateUpdateLock) {
                    lobbyPlayer.state = PlayerState.InPartyAsLeader
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PartyUpdateEvent::class.java)
            .handler { event ->
                event.party.includedMembersOnline().forEach { member ->
                    val lobbyPlayer = LobbyPlayerService.find(member)
                        ?: return@forEach

                    lobbyPlayer.player?.apply {
                        get(lobbyPlayer.state).applyToPlayer(this)
                    }
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerJoinEvent::class.java, EventPriority.MONITOR)
            .handler {
                if (it.player.uniqueId !in wasGameParticipant)
                {
                    with(PracticeConfigurationService.local()) {
                        if (loginMOTD.isNotEmpty())
                        {
                            loginMOTD.forEach(it.player::sendMessage)
                        }
                    }
                }

                Tasks.delayed(5L) {
                    loginTasks[it.player.uniqueId]
                        ?.forEach { task ->
                            task.invoke(it.player)
                        }
                }
            }

        Events
            .subscribe(PlayerJoinWithExpectationEvent::class.java)
            .handler {
                if (it.response.parameters.containsKey("was-game-participant"))
                {
                    wasGameParticipant += it.uniqueId
                }

                if (it.response.parameters.containsKey("requeue-kit-id"))
                {
                    val rematchKitID = it.response.parameters["requeue-kit-id"]
                    val rematchQueueType = QueueType.valueOf(
                        it.response.parameters["requeue-queue-type"]!!
                    )

                    val kit = KitService.cached().kits[rematchKitID]
                        ?: return@handler

                    loginTasks.getOrPut(it.uniqueId, ::mutableListOf) += { player ->
                        QueueService.joinQueue(
                            kit = kit,
                            queueType = rematchQueueType,
                            teamSize = 1,
                            player = player
                        )
                    }
                }

                if (it.response.parameters.containsKey("rematch-kit-id"))
                {
                    val rematchKitID = it.response.parameters["rematch-kit-id"]
                    val rematchTargetID = it.response.parameters["rematch-target-id"]

                    val kit = KitService.cached().kits[rematchKitID]
                        ?: return@handler

                    val rematchUuid = UUID.fromString(rematchTargetID)

                    if (rematchUuid in Globals.POSSIBLE_PLAYER_BOT_UNIQUE_IDS)
                    {
                        return@handler
                    }

                    loginTasks.getOrPut(it.uniqueId, ::mutableListOf) += { player ->
                        val username = rematchUuid.username()
                        val rematchItem = ItemBuilder
                            .of(Material.PAPER)
                            .name(
                                "${CC.D_GREEN}Rematch $username ${CC.GRAY}(Right Click)"
                            )
                            .build()

                        val terminable = CompositeTerminable.create()
                        Events
                            .subscribe(PlayerInteractEvent::class.java)
                            .filter { event ->
                                event.hasItem() &&
                                    event.action.name.contains("RIGHT") &&
                                    event.item.isSimilar(rematchItem)
                            }
                            .handler {
                                player.chat("/duel $username ${kit.id}")

                                Button.playNeutral(player)
                                terminable.closeAndReportException()
                            }
                            .bindWith(terminable)

                        Schedulers
                            .sync()
                            .runLater({
                                terminable.closeAndReportException()
                            }, 20L * 30L)
                            .bindWith(terminable)

                        Events
                            .subscribe(PlayerQuitEvent::class.java)
                            .filter { event ->
                                event.player.uniqueId == player.uniqueId
                            }
                            .handler {
                                terminable.closeAndReportException()
                            }
                            .bindWith(terminable)

                        terminable.with {
                            player.inventory.setItem(3, ItemStack(Material.AIR))
                        }

                        player.inventory.setItem(3, rematchItem)
                    }
                }
            }
            .bindWith(plugin)

        val kitEditor = DynamicHotbarPresetEntry().apply {
            onBuild = context@{ player ->
                if (MinigameLobby.isMinigameLobby() || MinigameLobby.isMainLobby())
                {
                    return@context ItemStack(Material.AIR)
                }

                return@context ItemBuilder(Material.BOOK)
                    .name("${CC.D_AQUA}Kit Editor ${CC.GRAY}(Right Click)")
                    .build()
            }

            onClick = context@{ player ->
                val profile = PracticeProfileService.find(player)
                    ?: return@context

                EditorKitSelectionMenu(player, profile).openMenu(player)
                Button.playNeutral(player)
            }
        }

        val settings = StaticHotbarPresetEntry(
            ItemBuilder(Material.REDSTONE_COMPARATOR)
                .name("${CC.D_PURPLE}Settings ${CC.GRAY}(Right Click)")
        ).also {
            it.onClick = { player ->
                SettingMenu(player).openMenu(player)
                Button.playNeutral(player)
            }
        }

        idlePreset.addSlot(
            7,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.WATCH)
                    .name("${CC.D_PURPLE}Navigator ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = { player ->
                    PlayerMainMenu().openMenu(player)
                }
            }
        )

        idlePreset.addSlot(
            0,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.IRON_SWORD)
                    .name("${CC.GREEN}Play Casual ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    if (player.hasMetadata("frozen"))
                    {
                        player.sendMessage("${CC.RED}You cannot join queues while frozen!")
                        return@context
                    }

                    openQueueMenuWithFormatSelect(player, QueueType.Casual, 1)
                }
            }
        )

        idlePreset.addSlot(
            2,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.GOLD_SWORD)
                    .name("${CC.GOLD}Play Bot Fights ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    if (player.hasMetadata("frozen"))
                    {
                        player.sendMessage("${CC.RED}You cannot join queues while frozen!")
                        return@context
                    }

                    val menu = buildBotSelectorMenu(player)
                        ?: return@context run {
                            player.sendMessage("${CC.RED}This feature is not available on this server!")
                        }

                    menu.openMenu(player)
                }
            }
        )

        idlePreset.addSlot(
            1,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.DIAMOND_SWORD)
                    .name("${CC.AQUA}Play Ranked ${CC.GRAY}(Right Click)")
                    .unbreakable()
            ).also {
                it.onClick = context@{ player ->
                    openRankedQueueMenu(player)
                }
            }
        )

        idlePreset.addSlot(
            4,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.NETHER_STAR)
                    .name("${CC.PINK}Create a Party ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    player.performCommand("party create")
                    Button.playNeutral(player)
                }
            }
        )

        idlePreset.addSlot(
            6,
            StaticHotbarPresetEntry(
                ItemBuilder(Material.ITEM_FRAME)
                    .name("${CC.YELLOW}View Leaderboards ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = { player ->
                    LeaderboardsMenu(player).openMenu(player)
                    Button.playNeutral(player)
                }
            }
        )

        idlePreset.addSlot(6, kitEditor)
        idlePreset.addSlot(8, settings)

        HotbarPresetHandler.startTrackingHotbar("idle", idlePreset)
        hotbarCache[PlayerState.Idle] = idlePreset

        val inQueuePreset = HotbarPreset()
        inQueuePreset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.RED}Leave Queue ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    QueueService.leaveQueue(player)
                    Button.playNeutral(player)
                    player.sendMessage(
                        "${CC.RED}You left the queue!"
                    )
                }
            }
        )

        inQueuePreset.addSlot(0, kitEditor)

        HotbarPresetHandler.startTrackingHotbar("inQueue", inQueuePreset)
        hotbarCache[PlayerState.InQueue] = inQueuePreset

        val inPartyPreset = HotbarPreset()
        inPartyPreset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.RED}Leave Party ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    player.performCommand("party leave")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.Idle
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        fun playerIsLeader(player: Player): Boolean
        {
            val lobbyPlayer = LobbyPlayerService.find(player)
                ?: return false

            if (!lobbyPlayer.isInParty())
            {
                return false
            }

            return lobbyPlayer.partyOf().delegate
                .leader.uniqueId == player.uniqueId
        }

        inPartyPreset.addSlot(
            0,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    return@scope ItemBuilder
                        .of(Material.BEACON)
                        .name("${CC.GOLD}Configure Party ${CC.GRAY}(Right Click)")
                        .build()
                }

                it.onClick = scope@{ player ->
                    if (!playerIsLeader(player))
                    {
                        return@scope
                    }

                    player.performCommand("party manage")
                }
            }
        )

        inPartyPreset.addSlot(
            1,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    return@scope ItemBuilder
                        .copyOf(
                            object : AddButton()
                            {}
                                .getButtonItem(player)
                        )
                        .name("${CC.B_GREEN}Invite Player ${CC.GRAY}(Right Click)")
                        .build()
                }

                it.onClick = scope@{ player ->
                    if (!playerIsLeader(player))
                    {
                        return@scope
                    }

                    InputPrompt()
                        .withText("${CC.B_GREEN}Type the player's username in chat to invite them!")
                        .acceptInput { _, playerName ->
                            player.performCommand("party invite $playerName")
                        }
                        .start(player)
                }
            }
        )

        inPartyPreset.addSlot(
            4,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    if (MinigameLobby.isMinigameLobby() || MinigameLobby.isMainLobby())
                    {
                        return@scope ItemStack(Material.AIR)
                    }

                    val party = NetworkPartyService.findParty(player.uniqueId)
                        ?: return@scope ItemStack(Material.AIR)
                    val privateGamesEnabled = party.isEnabled(PartySetting.PRIVATE_GAMES)

                    return@scope if (privateGamesEnabled)
                    {
                        ItemBuilder
                            .of(XMaterial.PINK_DYE)
                            .name("${CC.LIGHT_PURPLE}Private Games ${CC.GRAY}(Right Click)")
                            .build()
                    } else
                    {
                        ItemBuilder
                            .of(XMaterial.LIME_DYE)
                            .name("${CC.B_GREEN}Party Play ${CC.GRAY}(Right Click)")
                            .build()
                    }
                }

                it.onClick = scope@{ player ->
                    if (!playerIsLeader(player))
                    {
                        return@scope
                    }

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    if (!lobbyPlayer.isInParty())
                    {
                        return@scope
                    }

                    val party = lobbyPlayer.partyOf()
                    val privateGamesEnabled = party.delegate.isEnabled(mc.arch.minigames.parties.model.PartySetting.PRIVATE_GAMES)

                    if (privateGamesEnabled)
                    {
                        gg.tropic.practice.menu.party.PrivateGamesMinigameSelectorMenu().openMenu(player)
                    } else
                    {
                        PartyPlayGameSelectMenu().openMenu(player)
                    }
                }
            }
        )

        inPartyPreset.addSlot(7, kitEditor)
        inPartyPreset.addSlot(6, settings)

        HotbarPresetHandler.startTrackingHotbar("inPartyLeader", inPartyPreset)
        hotbarCache[PlayerState.InPartyAsLeader] = inPartyPreset

        val inPartyMemberPreset = HotbarPreset()
        inPartyMemberPreset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.RED}Leave Party ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    player.performCommand("party leave")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.Idle
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        inPartyMemberPreset.addSlot(7, kitEditor)
        inPartyMemberPreset.addSlot(6, settings)

        HotbarPresetHandler.startTrackingHotbar("inPartyMember", inPartyMemberPreset)
        hotbarCache[PlayerState.InPartyAsMember] = inPartyMemberPreset

        if (!SpigotNetworkMetadataDataSync.isFlagged("STRIPPED_LOBBY"))
        {
            Events
                .subscribe(
                    PlayerJoinEvent::class.java,
                    EventPriority.LOW
                )
                .handler { event ->
                    idlePreset.applyToPlayer(event.player)
                }
                .bindWith(plugin)
        }
    }

    fun get(state: PlayerState) = hotbarCache[state]!!
    fun set(state: PlayerState, preset: HotbarPreset)
    {
        hotbarCache[state] = preset
    }

    fun reconfigureForMinigames()
    {
        val settings = StaticHotbarPresetEntry(
            ItemBuilder(Material.REDSTONE_COMPARATOR)
                .name("${CC.D_PURPLE}Settings ${CC.GRAY}(Right Click)")
        ).also {
            it.onClick = { player ->
                SettingMenu(player).openMenu(player)
                Button.playNeutral(player)
            }
        }


        val inPartyPreset = HotbarPreset()
        inPartyPreset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.RED}Leave Party ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    player.performCommand("party leave")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.Idle
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        fun playerIsLeader(player: Player): Boolean
        {
            val lobbyPlayer = LobbyPlayerService.find(player)
                ?: return false

            if (!lobbyPlayer.isInParty())
            {
                return false
            }

            return lobbyPlayer.partyOf().delegate
                .leader.uniqueId == player.uniqueId
        }

        inPartyPreset.addSlot(
            0,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    return@scope ItemBuilder
                        .of(Material.BEACON)
                        .name("${CC.GOLD}Configure Party ${CC.GRAY}(Right Click)")
                        .build()
                }

                it.onClick = scope@{ player ->
                    if (!playerIsLeader(player))
                    {
                        return@scope
                    }

                    player.performCommand("party manage")
                }
            }
        )

        inPartyPreset.addSlot(
            3,
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

        if (MinigameLobby.isMinigameLobby())
        {
            inPartyPreset.addSlot(
                5,
                DynamicHotbarPresetEntry().also {
                    it.onBuild = scope@{ player ->
                        return@scope ItemBuilder
                            .of(XMaterial.LIME_DYE)
                            .name("${CC.GREEN}Play ${CC.GRAY}(Right Click)")
                            .build()
                    }

                    it.onClick = scope@{ player ->
                        MinigameLobby.customizer().playProvider(player)
                    }
                }
            )
        }

        inPartyPreset.addSlot(
            1,
            DynamicHotbarPresetEntry().also {
                it.onBuild = scope@{ player ->
                    return@scope ItemBuilder
                        .copyOf(
                            object : AddButton()
                            {}
                                .getButtonItem(player)
                        )
                        .name("${CC.B_GREEN}Invite Player ${CC.GRAY}(Right Click)")
                        .build()
                }

                it.onClick = scope@{ player ->
                    if (!playerIsLeader(player))
                    {
                        return@scope
                    }

                    InputPrompt()
                        .withText("${CC.B_GREEN}Type the player's username in chat to invite them!")
                        .acceptInput { _, playerName ->
                            player.performCommand("party invite $playerName")
                        }
                        .start(player)
                }
            }
        )

        inPartyPreset.addSlot(7, settings)

        HotbarPresetHandler.startTrackingHotbar("inPartyLeaderMinigames", inPartyPreset)
        hotbarCache[PlayerState.InPartyAsLeader] = inPartyPreset

        val inPartyMemberPreset = HotbarPreset()
        inPartyMemberPreset.addSlot(
            8,
            StaticHotbarPresetEntry(
                ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.RED}Leave Party ${CC.GRAY}(Right Click)")
            ).also {
                it.onClick = scope@{ player ->
                    player.performCommand("party leave")
                    Button.playNeutral(player)

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@scope

                    synchronized(lobbyPlayer.stateUpdateLock) {
                        lobbyPlayer.state = PlayerState.Idle
                        lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                    }
                }
            }
        )

        inPartyMemberPreset.addSlot(0, settings)

        HotbarPresetHandler.startTrackingHotbar("inPartyMemberMinigames", inPartyMemberPreset)
        hotbarCache[PlayerState.InPartyAsMember] = inPartyMemberPreset

        if (Bukkit.getPluginManager().isPluginEnabled("ScQueue"))
        {
            val inNetworkQueuePreset = HotbarPreset()
            inNetworkQueuePreset.addSlot(
                8,
                StaticHotbarPresetEntry(
                    ItemBuilder(XMaterial.RED_DYE)
                        .name("${CC.RED}Leave Queue ${CC.GRAY}(Right Click)")
                ).also {
                    it.onClick = scope@{ player ->
                        runCatching {
                            LeaveQueueCommand.onJoinQueue(player)
                        }.onFailure { throwable ->
                            if (throwable is ConditionFailedException)
                            {
                                player.sendMessage("${CC.RED}${throwable.message}")
                            }
                        }

                        val lobbyPlayer = LobbyPlayerService.find(player)
                            ?: return@scope

                        synchronized(lobbyPlayer.stateUpdateLock) {
                            lobbyPlayer.state = PlayerState.Idle
                            lobbyPlayer.maintainStateTimeout = System.currentTimeMillis() + 1000L
                        }
                    }
                }
            )

            inNetworkQueuePreset.addSlot(0, settings)

            HotbarPresetHandler.startTrackingHotbar("inNetworkQueue", inNetworkQueuePreset)
            hotbarCache[PlayerState.InNetworkQueue] = inNetworkQueuePreset
        }
    }

    fun reset(player: Player)
    {
        if (SpigotNetworkMetadataDataSync.isFlagged("STRIPPED_LOBBY"))
        {
            return
        }

        val lobbyPlayer = LobbyPlayerService.find(player.uniqueId)

        if (lobbyPlayer != null)
        {
            val state = lobbyPlayer.state
            val hotbar = get(state)

            if (!Bukkit.isPrimaryThread())
            {
                Tasks.sync {
                    hotbar.applyToPlayer(player)
                    return@sync
                }
            } else
            {
                hotbar.applyToPlayer(player)
            }
        }
    }
}
