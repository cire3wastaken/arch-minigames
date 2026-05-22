package gg.tropic.practice.resources

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.metadata.SpigotNetworkMetadataDataSync
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.channel.ChatChannelBuilder
import gg.scala.lemon.channel.ChatChannelService
import gg.scala.lemon.channel.channels.DefaultChatChannel
import gg.scala.lemon.command.ListCommand
import gg.scala.lemon.handler.PlayerHandler
import gg.scala.lemon.player.LemonPlayer
import gg.scala.lemon.player.spatial.tablist.events.TabSyncSubscribeEvent
import gg.scala.lemon.util.QuickAccess
import gg.tropic.practice.PracticeGame
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.event.PlayerMiniGameQuitWhileStartingEvent
import gg.tropic.practice.services.NetworkFluidityService
import gg.tropic.practice.ugc.WorldInstanceProviderType
import gg.tropic.practice.ugc.toHostedWorld
import gg.tropic.practice.extensions.PlayerMatchUtilities
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.utils.Players
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.ServerVersion
import net.evilblock.cubed.visibility.PlayerListVisibilityPolicy
import net.evilblock.cubed.visibility.VisibilityHandler
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 8/24/2024
 */
@Service
object MinigameServerFrontendService
{
    @Inject
    lateinit var plugin: PracticeGame

    @Configure
    fun configure()
    {
        Events
            .subscribe(TabSyncSubscribeEvent::class.java)
            .handler { event ->
                if (event.player.toHostedWorld() != null)
                {
                    event.isCancelled = true
                    return@handler
                }

                val game = GameService.byPlayerOrSpectator(event.player.uniqueId)
                    ?: return@handler

                if (game.miniGameLifecycle != null)
                {
                    event.isCancelled = true
                }
            }
            .bindWith(plugin)

        val gameSpecificChannel = ChatChannelBuilder.Companion.newBuilder()
            .import(DefaultChatChannel)
            .identifier("isolated-global")
            .compose()

        gameSpecificChannel.override(100) {
            val hostedWorld = it.toHostedWorld()
            if (hostedWorld != null && hostedWorld.providerType != WorldInstanceProviderType.REALM)
            {
                return@override true
            }

            val game = GameService.byPlayerOrSpectator(it.uniqueId)
                ?: return@override false

            return@override game.miniGameLifecycle != null
        }

        gameSpecificChannel.monitor()
        gameSpecificChannel.displayToPlayer { uniqueId, player ->
            val bukkitSender = Bukkit.getPlayer(uniqueId)
                ?: return@displayToPlayer false

            val hostedWorldSender = bukkitSender.toHostedWorld()
            val hostedWorldReceiver = player.toHostedWorld()

            if (hostedWorldReceiver != null &&
                hostedWorldReceiver.globalId == hostedWorldSender?.globalId)
            {
                return@displayToPlayer true
            }

            val senderGame = GameService.byPlayerOrSpectator(bukkitSender.uniqueId)
                ?: return@displayToPlayer false

            val selfGame = GameService.byPlayerOrSpectator(player.uniqueId)
                ?: return@displayToPlayer false

            senderGame.miniGameLifecycle != null && selfGame.identifier == senderGame.identifier
        }

        ChatChannelService.register(gameSpecificChannel)

        val spectatorSpecificChannel = ChatChannelBuilder.Companion.newBuilder()
            .identifier("minigame-spectator")
            .format { sender, receiver, message, server, rank ->
                Component.text("${CC.GRAY}[Spectator] ")
                    .append(
                        DefaultChatChannel.format(sender, receiver, message, server, rank)
                    )
            }
            .compose()

        spectatorSpecificChannel.override(130) {
            val game = GameService.byPlayerOrSpectator(it.uniqueId)
                ?: return@override false

            return@override game.miniGameLifecycle != null &&
                GameService.isSpectating(it) &&
                game.state(GameState.Playing) &&
                !game.shouldKeepCentralChat
        }

        spectatorSpecificChannel.monitor()
        spectatorSpecificChannel.displayToPlayer { uniqueId, player ->
            val bukkitSender = Bukkit.getPlayer(uniqueId)
                ?: return@displayToPlayer false

            val senderGame = GameService.byPlayerOrSpectator(bukkitSender.uniqueId)
                ?: return@displayToPlayer false

            val selfGame = GameService.byPlayerOrSpectator(player.uniqueId)
                ?: return@displayToPlayer false

            senderGame.miniGameLifecycle != null &&
                selfGame.identifier == senderGame.identifier &&
                GameService.isSpectating(bukkitSender) &&
                GameService.isSpectating(player) &&
                !selfGame.shouldKeepCentralChat
        }

        ChatChannelService.register(spectatorSpecificChannel)

        val teamSpecificChannel = ChatChannelBuilder.Companion.newBuilder()
            .identifier("minigame-team")
            .format { sender, receiver, message, server, rank ->
                Component.text("${CC.GOLD}[TEAM]${CC.R} ")
                    .append(
                        DefaultChatChannel.format(sender, receiver, message, server, rank)
                    )
            }
            .compose()

        teamSpecificChannel.override(125) {
            val game = GameService.byPlayerOrSpectator(it.uniqueId)
                ?: return@override false

            return@override game.miniGameLifecycle != null
                && game.ensurePlaying()
                && game.miniGameLifecycle!!.configuration.maximumPlayersPerTeam > 1
                && game.teams.size > 1
                && !game.shouldKeepCentralChat
        }

        teamSpecificChannel.monitor()
        teamSpecificChannel.displayToPlayer { uniqueId, player ->
            val bukkitSender = Bukkit.getPlayer(uniqueId)
                ?: return@displayToPlayer false

            val senderGame = GameService.byPlayerOrSpectator(bukkitSender.uniqueId)
                ?: return@displayToPlayer false

            val selfGame = GameService.byPlayerOrSpectator(player.uniqueId)
                ?: return@displayToPlayer false

            senderGame.miniGameLifecycle != null &&
                selfGame.identifier == senderGame.identifier &&
                !GameService.isSpectating(bukkitSender) &&
                !GameService.isSpectating(player) &&
                senderGame.getTeamOf(bukkitSender.uniqueId)?.teamIdentifier == senderGame.getTeamOf(player.uniqueId)?.teamIdentifier
        }

        ChatChannelService.register(teamSpecificChannel)

        ListCommand.supplyCustomPlayerList { sender ->
            if (sender !is Player)
            {
                return@supplyCustomPlayerList ListCommand.PlayerList(
                    maxCount = Bukkit.getMaxPlayers(),
                    sortedPlayerEntries = PlayerHandler.getCorrectedPlayerList(
                        sender,
                        Bukkit.getOnlinePlayers().toList()
                    ).map(LemonPlayer::getColoredName)
                )
            }

            val hostedWorld = sender.toHostedWorld()
            if (hostedWorld != null)
            {
                return@supplyCustomPlayerList ListCommand.PlayerList(
                    maxCount = 30, // TODO: change based on hosted world config
                    sortedPlayerEntries = PlayerHandler.getCorrectedPlayerList(
                        sender,
                        hostedWorld.onlinePlayers().toList()
                    ).map(LemonPlayer::getColoredName)
                )
            }

            val game = GameService.byPlayerOrSpectator(sender.uniqueId)
                ?: return@supplyCustomPlayerList NetworkFluidityService.playerList

            if (game.miniGameLifecycle != null)
            {
                return@supplyCustomPlayerList ListCommand.PlayerList(
                    maxCount = game.miniGameLifecycle!!.configuration.maximumPlayers,
                    sortedPlayerEntries = PlayerHandler.getCorrectedPlayerList(
                        sender,
                        game.allNonSpectators()
                    ).map(LemonPlayer::getColoredName)
                )
            }

            return@supplyCustomPlayerList NetworkFluidityService.playerList
        }

        VisibilityHandler.selectivelySetPolicy { player ->
            if (player.toHostedWorld() != null)
            {
                return@selectivelySetPolicy PlayerListVisibilityPolicy.World
            }

            val game = GameService.byPlayerOrSpectator(player.uniqueId)
                ?: return@selectivelySetPolicy run {
                    if (SpigotNetworkMetadataDataSync.isFlagged("VIS_POLICY_LOG"))
                    {
                        println("${player.name} selected Global - no game")
                    }
                    PlayerListVisibilityPolicy.Global
                }

            if (game.miniGameLifecycle != null)
            {
                if (SpigotNetworkMetadataDataSync.isFlagged("VIS_POLICY_LOG"))
                {
                    println("${player.name} selected World - minigame")
                }
                return@selectivelySetPolicy PlayerListVisibilityPolicy.World
            }

            if (SpigotNetworkMetadataDataSync.isFlagged("VIS_POLICY_LOG"))
            {
                println("${player.name} selected Global - duels")
            }
            return@selectivelySetPolicy PlayerListVisibilityPolicy.Global
        }

        NetworkFluidityService.provideLocalDuelsPlayers {
            Players
                .all()
                .filter {
                    val game = GameService.byPlayerOrSpectator(it.uniqueId)
                        ?: return@filter true

                    game.miniGameLifecycle == null
                }
        }

        /**
         * Dynamically edit all CommandManager instances' player completions
         */
        Schedulers
            .sync()
            .runLater({
                Bukkit.getPluginManager().plugins
                    .filterIsInstance<ExtendedScalaPlugin>()
                    .forEach { plugin ->
                        plugin.commandManager.commandCompletions.registerCompletion("players") {
                            val input = it.input
                            val sender = it.sender

                            if (sender is Player)
                            {
                                if (sender.toHostedWorld() != null)
                                {
                                    return@registerCompletion PlayerMatchUtilities.getPlayerMatches(
                                        sender,
                                        input,
                                        sender.world.players
                                    )
                                }

                                val game = GameService.byPlayerOrSpectator(sender.uniqueId)
                                if (game != null && game.miniGameLifecycle != null)
                                {
                                    return@registerCompletion PlayerMatchUtilities.getPlayerMatches(
                                        sender,
                                        input,
                                        game.toBukkitPlayers().filterNotNull()
                                    )
                                }
                            }

                            return@registerCompletion PlayerMatchUtilities.getPlayerMatches(
                                sender,
                                input,
                                Bukkit.getOnlinePlayers()
                            )
                        }
                    }
            }, 1L)

        Events
            .subscribe(PlayerMiniGameQuitWhileStartingEvent::class.java)
            .handler {
                it.game.preWaitRemove(it.player)
                it.game.sendMessage("${QuickAccess.coloredName(it.player)}${CC.YELLOW} has left.")
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game is AbstractMiniGameGameImpl<*> }
            .handler {
                if (it.game.state(GameState.Waiting) || it.game.state(GameState.Starting))
                {
                    val miniGame = it.game as AbstractMiniGameGameImpl<*>
                    it.player.gameMode = GameMode.ADVENTURE
                    it.game.sendMessage("${QuickAccess.coloredName(it.player)}${CC.YELLOW} has joined (${CC.AQUA}${
                        // The reason we add 1, is because on Legacy versions, the player count of the current 
                        // world does not update at this point, and we have to overcompensate
                        it.game.arenaWorld.players.size + (if (ServerVersion.getVersion().isNewerThan(ServerVersion.v1_10_R1)) 0 else 1)
                    }${CC.YELLOW}/${CC.AQUA}${
                        miniGame.miniGameLifecycle!!
                            .configuration
                            .maximumPlayers
                    }${CC.YELLOW})!")
                }
            }
            .bindWith(plugin)
    }
}
