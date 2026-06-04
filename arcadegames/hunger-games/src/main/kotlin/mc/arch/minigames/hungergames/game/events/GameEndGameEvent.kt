package mc.arch.minigames.hungergames.game.events

import gg.tropic.practice.minigame.MiniGameEvent
import mc.arch.minigames.hungergames.game.HungerGamesLifecycle
import java.time.Duration

/**
 * @author ArchMC
 */
class GameEndGameEvent(
    private val lifecycle: HungerGamesLifecycle,
    override val description: String = "Game End",
    override val duration: Duration = Duration.ofSeconds(230)
) : MiniGameEvent
{
    override fun execute()
    {
        lifecycle.game.complete(null)
    }
}
