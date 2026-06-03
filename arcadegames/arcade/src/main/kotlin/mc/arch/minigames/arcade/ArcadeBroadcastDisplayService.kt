package mc.arch.minigames.arcade

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings

@Service
object ArcadeBroadcastDisplayService
{
    @Configure
    fun configure()
    {
        ArcadeBroadcastTrigger.installInteractListener()
        ArcadePrivateGameSettings.register()
    }
}
