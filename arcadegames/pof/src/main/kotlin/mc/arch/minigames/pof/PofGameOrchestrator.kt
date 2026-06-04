package mc.arch.minigames.pof

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.BasicMiniGameOrchestrator
import mc.arch.minigames.pof.PofGameConfiguration
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import me.lucko.helper.Events

@Service
object PofGameOrchestrator : BasicMiniGameOrchestrator<PofGameConfiguration>()
{
    override val id = "pof"
    override fun prepare(
        miniGame: AbstractMiniGameGameImpl<PofGameConfiguration>,
        configuration: PofGameConfiguration
    ) = PofGameLifecycle(configuration, miniGame)

    @Configure
    fun configure()
    {
        ArcadeBroadcastTrigger.installInteractListener()

        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game.miniGameLifecycle is PofGameLifecycle }
            .filter { it.game.state(GameState.Waiting) || it.game.state(GameState.Starting) }
            .handler { ArcadeBroadcastTrigger.giveItemIfPermitted(it.player, slot = 1) }
    }
}
