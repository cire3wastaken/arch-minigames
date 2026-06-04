package mc.arch.minigames.arcade

import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.BasicMiniGameOrchestrator
import gg.tropic.practice.minigame.MiniGameLifecycle
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings
import mc.arch.minigames.arcade.rlgl.RlglArcadeLifecycle
import mc.arch.minigames.arcade.oitc.OitcArcadeLifecycle
import mc.arch.minigames.arcade.sumo.SumoArcadeLifecycle

/**
 * @author Subham
 * @since 7/26/25
 */
@Service
object ArcadeMiniGameOrchestrator : BasicMiniGameOrchestrator<ArcadeMiniGameConfiguration>()
{
    override val id = "arcade"

    override fun prepare(
        miniGame: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
        configuration: ArcadeMiniGameConfiguration
    ): MiniGameLifecycle<ArcadeMiniGameConfiguration>
    {
        // Registered here (not via @Configure) so it's guaranteed to run on the game
        // server before any private game's settings menu can open. Idempotent.
        ArcadePrivateGameSettings.register()

        return when (configuration.mode)
        {
            ArcadeMode.SUMO -> SumoArcadeLifecycle(configuration, miniGame)
            ArcadeMode.OITC -> OitcArcadeLifecycle(configuration, miniGame)
            ArcadeMode.RED_LIGHT_GREEN_LIGHT -> RlglArcadeLifecycle(configuration, miniGame)
        }
    }
}
