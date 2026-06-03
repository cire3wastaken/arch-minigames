package mc.arch.minigames.arcade

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcast
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings
import org.bukkit.Bukkit

/**
 * Mirrors the arcade queue broadcast onto the game servers so players already
 * waiting in a game can see that someone announced an open queue.
 *
 * Only players sitting in a waiting/starting game are notified; players in an
 * active match are left undisturbed.
 *
 * @author Michele
 */
@Service
object ArcadeBroadcastDisplayService
{
    @Configure
    fun configure()
    {
        ArcadeBroadcast.listen { broadcaster, game, queueId ->
            val message = ArcadeBroadcast.render(broadcaster, game, queueId)

            Bukkit.getOnlinePlayers().forEach { player ->
                val playerGame = GameService.byPlayer(player) ?: return@forEach
                if (playerGame.state(GameState.Waiting) || playerGame.state(GameState.Starting))
                {
                    message.sendToPlayer(player)
                }
            }
        }

        ArcadeBroadcastTrigger.installInteractListener()

        // Guaranteed-at-startup registration of the per-mode private game settings.
        // This @Configure is known to run (the broadcast listener above works), whereas
        // ArcadePrivateGameSettings' own module isn't reliably service-scanned.
        ArcadePrivateGameSettings.register()
    }
}
