package mc.arch.minigames.arcade.lobby.broadcast

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastPolicy
import mc.arch.minigames.arcade.lobby.menu.ArcadeCatalog

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

        // Queue broadcasts are delivered network-wide by Lemon (see ArcadeBroadcast.publish),
        // so no local listener is needed here anymore.
    }
}
