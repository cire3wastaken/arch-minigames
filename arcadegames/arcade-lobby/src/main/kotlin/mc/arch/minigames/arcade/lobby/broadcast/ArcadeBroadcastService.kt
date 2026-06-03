package mc.arch.minigames.arcade.lobby.broadcast

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcast
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastPolicy
import mc.arch.minigames.arcade.lobby.menu.ArcadeCatalog
import me.lucko.helper.utils.Players

@Service
object ArcadeBroadcastService
{
    @Configure
    fun configure()
    {
        ArcadeBroadcastPolicy.publishCatalog(
            ArcadeCatalog.cards
                .flatMap { card -> card.modes.map { it.queueId to card.cardName } }
                .toMap()
        )

        ArcadeBroadcast.listen { broadcaster, game, queueId ->
            val message = ArcadeBroadcast.render(broadcaster, game, queueId)
            Players.all().forEach(message::sendToPlayer)
        }
    }
}
