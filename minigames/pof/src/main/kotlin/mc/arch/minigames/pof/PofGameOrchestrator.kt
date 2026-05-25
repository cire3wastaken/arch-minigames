package mc.arch.minigames.pof

import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.BasicMiniGameOrchestrator
import mc.arch.minigame.pof.PofGameConfiguration

@Service
object PofGameOrchestrator : BasicMiniGameOrchestrator<PofGameConfiguration>()
{
    override val id = "pof"
    override fun prepare(
        miniGame: AbstractMiniGameGameImpl<PofGameConfiguration>,
        configuration: PofGameConfiguration
    ) = PofGameLifecycle(configuration, miniGame)
}
