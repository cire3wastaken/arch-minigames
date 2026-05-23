package gg.tropic.practice.expectation

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.redirection.expectation.PlayerRedirectExpectationEvent
import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.scala.staff.moderation.jump.PlayerExpectedToJumpEvent
import gg.tropic.practice.PracticeGame
import gg.tropic.practice.extensions.resetAttributes
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.AsyncPlayerPreJoinGameEvent
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.games.event.PlayerSelectSpawnLocationEvent
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.PlayerMiniGameAttemptRejoinWithTokenEvent
import gg.tropic.practice.minigame.event.PlayerMiniGameRejoinWithTokenEvent
import gg.tropic.practice.minigame.event.PlayerMiniGameSpectateEvent
import gg.tropic.practice.minigame.event.PlayerMiniGameSpectateWithTokenEvent
import gg.tropic.practice.minigame.menu.SpectatorMenu
import gg.tropic.practice.minigame.rejoin.RejoinToken
import gg.tropic.practice.games.tasks.lifecycle.PlayerRespawnTask
import gg.tropic.practice.strategies.MarkSpectatorStrategy
import gg.tropic.practice.ugc.HostedWorldInstanceService
import gg.tropic.practice.ugc.toHostedWorld
import gg.tropic.practice.versioned.Versioned
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import net.evilblock.cubed.serializers.Serializers
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.ServerVersion
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import org.bukkit.Bukkit
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.spigotmc.event.player.PlayerSpawnLocationEvent
import java.util.*
import java.util.logging.Level
import okio.withLock

/**
 * @author GrowlyX
 * @since 8/4/2022
 */
@Service
object ExpectationService
{
    @Inject
    lateinit var plugin: PracticeGame

    @Inject
    lateinit var audiences: BukkitAudiences

    val returnToSpawnItem = ItemBuilder.of(XMaterial.RED_DYE)
        .name("${CC.RED}Return to Spawn ${CC.GRAY}(Right Click)")
        .build()

    val broadcastItem = ItemBuilder.of(XMaterial.CLOCK)
        .name("${CC.RED}Broadcast ${CC.GRAY}(Right Click)")
        .build()

    val spectateItem = ItemBuilder.of(XMaterial.COMPASS)
        .name("${CC.RED}Spectate Menu ${CC.GRAY}(Right Click)")
        .build()

    @Configure
    fun configure()
    {
        val expectedRejoinWithTokens = mutableMapOf<UUID, RejoinToken>()

        Events
            .subscribe(PlayerExpectedToJumpEvent::class.java)
            .handler {
                it.target.toHostedWorld()?.apply {
                    HostedWorldInstanceService.trackPendingLogin(it.player, this)
                    return@handler
                }

                val game = GameService.byPlayer(it.target)
                    ?: return@handler

                game.expectedSpectators += it.player
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerRedirectExpectationEvent::class.java)
            .handler { event ->
                if (event.parameters.containsKey("rejoin"))
                {
                    val token = Serializers.gson.fromJson(
                        event.parameters["rejoin"]!!,
                        RejoinToken::class.java
                    )

                    expectedRejoinWithTokens[event.uniqueId] = token
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(
                AsyncPlayerPreLoginEvent::class.java,
                EventPriority.HIGH
            )
            .filter { it.loginResult == AsyncPlayerPreLoginEvent.Result.ALLOWED }
            .handler { event ->
                val pendingHostedWorldLogin = HostedWorldInstanceService
                    .pendingLogin(event.uniqueId)

                if (pendingHostedWorldLogin != null)
                {
                    HostedWorldInstanceService
                        .instanceById(pendingHostedWorldLogin)
                        ?: return@handler run {
                            event.disallow(
                                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                                "${CC.RED}You do not have a hosted world to join!"
                            )
                        }

                    // We can let them enter
                    event.allow()
                    return@handler
                }

                val game = expectedRejoinWithTokens[event.uniqueId]
                    ?.let { rejoinToken ->
                        GameService.gameMappings[rejoinToken.expectation]
                    }
                    ?: GameService
                        .iterativeByPlayerOrSpectator(event.uniqueId)
                    ?: return@handler event.disallow(
                        AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        "${CC.RED}You do not have a game to join!"
                    )

                // Safety cleanup: remove player from any OTHER game's player list they shouldn't be in.
                // This catches stale references from race conditions in the matchmaking pipeline.
                GameService.gameMappings.values
                    .filter { it.identifier != game.identifier }
                    .forEach { otherGame ->
                        if (event.uniqueId in otherGame.toPlayers())
                        {
                            otherGame.teamMutLock.withLock {
                                otherGame.expectationModel.players -= event.uniqueId
                                otherGame.getNullableTeamOfID(event.uniqueId)?.players?.remove(event.uniqueId)
                            }
                            plugin.logger.warning(
                                "[double-match-fix] Cleaned up stale player reference: ${event.uniqueId} was in game ${otherGame.identifier} but logging into game ${game.identifier}"
                            )
                        }
                    }

                if (game.state(GameState.Playing) && event.uniqueId !in game.expectedSpectators && game.miniGameLifecycle != null)
                {
                    if (!expectedRejoinWithTokens.containsKey(event.uniqueId))
                    {
                        event.disallow(
                            AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                            "${CC.RED}You cannot join this game as it has already started!"
                        )
                        return@handler
                    } else
                    {
                        val token = (game as AbstractMiniGameGameImpl<*>)
                            .playerTracker
                            .getRejoinToken(event.uniqueId)
                            ?: return@handler run {
                                event.disallow(
                                    AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                                    "${CC.RED}You cannot join this game anymore!"
                                )
                            }

                        val rejoinEvent = PlayerMiniGameAttemptRejoinWithTokenEvent(
                            game = game.miniGameLifecycle!!,
                            previousTeamIdentifier = token.previousTeam,
                            player = event.uniqueId
                        )
                        Bukkit.getPluginManager().callEvent(rejoinEvent)

                        if (!rejoinEvent.shouldAllowLogin)
                        {
                            event.disallow(
                                AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                                "${CC.RED}You cannot join this game anymore!"
                            )
                            return@handler
                        }

                        val rejoinSuccess = game.playerTracker.useRejoinToken(event.uniqueId)
                        if (rejoinEvent.persistentSpectator || !rejoinSuccess)
                        {
                            // If rejoin failed (team gone) or persistent spectator, add as spectator
                            game.expectedSpectators += event.uniqueId
                            GameService.spectatorToGameMappings[event.uniqueId] = game
                        } else
                        {
                            GameService.playerToGameMappings[event.uniqueId] = game
                        }
                        return@handler
                    }
                }

                if (event.uniqueId in game.expectedSpectators)
                {
                    GameService.spectatorToGameMappings[event.uniqueId] = game
                } else
                {
                    if (game.state(GameState.Waiting) || game.state(GameState.Starting))
                    {
                        val preJoinEvent = AsyncPlayerPreJoinGameEvent(game, event.uniqueId)
                        Bukkit.getPluginManager().callEvent(preJoinEvent)
                    }

                    GameService.playerToGameMappings[event.uniqueId] = game
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(AsyncPlayerPreLoginEvent::class.java, EventPriority.HIGHEST)
            .filter { event -> event.result != PlayerPreLoginEvent.Result.ALLOWED }
            .handler {
                expectedRejoinWithTokens.remove(it.uniqueId)
            }
            .bindWith(plugin)

        Events
            .subscribe(
                PlayerQuitEvent::class.java,
                EventPriority.MONITOR
            )
            .handler {
                GameService.spectatorToGameMappings.remove(it.player.uniqueId)

                // Clean up readyPlayers tracking before removing the mapping
                GameService.byPlayer(it.player)?.readyPlayers?.remove(it.player.uniqueId)

                GameService.playerToGameMappings.remove(it.player.uniqueId)
                PlayerRespawnTask.cancel(it.player.uniqueId)

                expectedRejoinWithTokens.remove(it.player.uniqueId)
            }
            .bindWith(plugin)

        fun findBestAvailableLobby(group: String): GameServer?
        {
            return ServerContainer
                .getServersInGroupCasted<GameServer>(group)
                .filter {
                    it.getWhitelisted() == ServerSync.getLocalGameServer().getWhitelisted()
                }
                .minByOrNull {
                    it.getPlayersCount() ?: Int.MAX_VALUE // we don't want to send the player to a broken server >-<
                }
        }

        Events
            .subscribe(PlayerInteractEvent::class.java)
            .filter { !it.isCancelled }
            .filter {
                it.hasItem() &&
                    (it.action == Action.RIGHT_CLICK_BLOCK || it.action == Action.RIGHT_CLICK_AIR) &&
                    it.item.isSimilar(returnToSpawnItem)
            }
            .handler {
                it.player.toHostedWorld()?.apply {
                    val lobby = findBestAvailableLobby(providerType.lobbyGroup)
                    if (lobby != null)
                    {
                        it.player.sendMessage("${CC.GRAY}Sending you to the lobby...")
                        VelocityRedirectSystem.redirect(it.player, lobby.id)
                    }
                    return@handler
                }

                val game = GameService
                    .byPlayerOrSpectator(it.player.uniqueId)
                    ?: return@handler

                game.findBestAvailableLobby()?.apply {
                    VelocityRedirectSystem.redirect(it.player, id)
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerInteractEvent::class.java)
            .filter {
                it.hasItem() &&
                    (it.action == Action.RIGHT_CLICK_BLOCK || it.action == Action.RIGHT_CLICK_AIR) &&
                    it.item.isSimilar(spectateItem)
            }
            .filter {
                GameService.isSpectating(it.player)
            }
            .handler {
                val game = GameService
                    .byPlayerOrSpectator(it.player.uniqueId)
                    ?: return@handler

                SpectatorMenu(it.player, game).openMenu(it.player)
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerSpawnLocationEvent::class.java)
            .handler {
                val pendingHostedWorldLogin = HostedWorldInstanceService
                    .pendingLoginInstance(it.player.uniqueId)

                // We only really need this on 1.8... I hope
                if (ServerVersion.getVersion().isOlderThan(ServerVersion.v1_9))
                {
                    Versioned.toProvider().getPlayerProvider().setSurvivalGameMode(it.player)
                }

                if (pendingHostedWorldLogin != null)
                {
                    HostedWorldInstanceService.removePendingLogin(it.player.uniqueId)
                    HostedWorldInstanceService.linkPlayerToInstance(it.player, pendingHostedWorldLogin)

                    it.spawnLocation = pendingHostedWorldLogin.playerSpawnLocation()
                    return@handler
                }

                val game = GameService
                    .byPlayerOrSpectator(it.player.uniqueId)
                    ?: return@handler

                game.pendingLogins.remove(it.player.uniqueId)

                val spawnLocation = if (it.player.uniqueId !in game.expectedSpectators)
                {
                    val team = game.getNullableTeam(it.player)
                    if (team == null)
                    {
                        plugin.logger.warning(
                            "Game ${game.expectation} on ${game.map.name} has no team for player ${it.player.name} (likely timed-out pending login)"
                        )
                        null
                    } else
                    {
                        runCatching {
                            game.map
                                .findSpawnLocationMatchingTeam(team.teamIdentifier)!!
                                .toLocation(game.arenaWorld)
                        }.onFailure { throwable ->
                            plugin.logger.log(
                                Level.WARNING,
                                "Game ${game.expectation} on ${game.map.name} has no spawn location for player ${it.player.name} (team=${team.teamIdentifier})",
                                throwable
                            )
                        }.getOrNull()
                    }
                } else
                {
                    game.arenaWorld.players
                        .firstOrNull()
                        ?.location
                }

                val event = PlayerSelectSpawnLocationEvent(
                    game, it.player,
                    spawnLocation ?: game.arenaWorld.spawnLocation
                    ?: return@handler run {
                        println("Player ${it.player.name} tried logging in with no game spawn location")
                        it.spawnLocation = Bukkit.getWorld("world").spawnLocation

                        Schedulers
                            .sync()
                            .runLater({
                                it.player.sendMessage("${CC.RED}We were unable to teleport you into a game. Please use /spawn to return to the lobby.")
                            }, 20L)
                    },
                    it.player.uniqueId in game.expectedSpectators
                )

                runCatching {
                    Bukkit.getPluginManager().callEvent(event)
                }.onFailure { throwable ->
                    throwable.printStackTrace()
                }

                it.spawnLocation = event.location
            }
            .bindWith(plugin)

        Events
            .subscribe(
                PlayerQuitEvent::class.java,
                EventPriority.LOWEST
            )
            .handler { event ->
                event.player.toHostedWorld()?.apply {
                    onLogout(event.player)
                    HostedWorldInstanceService.unlinkPlayer(event.player)
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(
                PlayerJoinEvent::class.java,
                EventPriority.MONITOR
            )
            .handler {
                it.player.toHostedWorld()?.apply {
                    onLogin(it.player)
                    return@handler
                }

                val game = GameService
                    .byPlayerOrSpectator(it.player.uniqueId)
                    ?: return@handler

                it.player.resetAttributes()

                PlayerRespawnTask.cancel(it.player.uniqueId)

                val isRejoiningGame = expectedRejoinWithTokens.containsKey(it.player.uniqueId)
                if (it.player.uniqueId in game.expectedSpectators)
                {
                    MarkSpectatorStrategy.markSpectator(
                        player = it.player,
                        world = game.arenaWorld
                    )

                    if (isRejoiningGame)
                    {
                        if (game.miniGameLifecycle != null)
                        {
                            Bukkit.getPluginManager().callEvent(PlayerMiniGameSpectateWithTokenEvent(game.miniGameLifecycle!!, it.player))
                            (game as AbstractMiniGameGameImpl<*>).playerTracker.removeToken(it.player)
                        }
                    }

                    if (game.miniGameLifecycle != null)
                    {
                        Bukkit.getPluginManager().callEvent(PlayerMiniGameSpectateEvent(game.miniGameLifecycle!!, it.player))
                    }
                } else
                {
                    if (isRejoiningGame)
                    {
                        if (game.miniGameLifecycle != null)
                        {
                            Bukkit.getPluginManager().callEvent(PlayerMiniGameRejoinWithTokenEvent(game.miniGameLifecycle!!, it.player))
                            (game as AbstractMiniGameGameImpl<*>).playerTracker.removeToken(it.player)
                        }
                    }

                    if (game.robot())
                    {
                        game.robotInstance.forEach { robot ->
                            robot.participantConnected(it.player)
                        }
                    }

                    // Mark the player as fully connected and ready.
                    // startIfReady() checks this set instead of just Bukkit.getPlayer() to avoid
                    // starting the game before the player has fully loaded in.
                    game.readyPlayers += it.player.uniqueId

                    Bukkit.getPluginManager().callEvent(PlayerJoinGameEvent(game, it.player))
                    if (!isRejoiningGame)
                    {
                        GameService.spectatorPlayers -= it.player.uniqueId
                    }
                }
            }
            .bindWith(plugin)
    }
}
