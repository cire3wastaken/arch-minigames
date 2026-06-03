package mc.arch.minigames.arcade

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcast
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings
import org.bukkit.Bukkit

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
        ArcadePrivateGameSettings.register()
    }
}
