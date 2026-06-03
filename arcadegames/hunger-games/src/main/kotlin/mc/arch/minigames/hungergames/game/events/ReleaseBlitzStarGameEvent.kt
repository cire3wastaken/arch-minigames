package mc.arch.minigames.hungergames.game.events

import gg.tropic.practice.minigame.MiniGameEvent
import mc.arch.minigames.hungergames.game.HungerGamesLifecycle
import net.evilblock.cubed.util.CC
import org.bukkit.Sound
import java.time.Duration

/**
 * @author ArchMC
 */
class ReleaseBlitzStarGameEvent(
    private val lifecycle: HungerGamesLifecycle,
    override val description: String = "The Star",
    override val duration: Duration = Duration.ofSeconds(120)
) : MiniGameEvent
{
    override fun execute()
    {
        lifecycle.blitzStarInChest = true

        lifecycle.game.sendMessage("")
        lifecycle.game.sendMessage("${CC.D_AQUA}${CC.BOLD}The Blitz Star has been released!")
        lifecycle.game.sendMessage("${CC.GRAY}Find it in a chest to gain a special ability!")
        lifecycle.game.sendMessage("")

        lifecycle.game.playSound(Sound.WITHER_SPAWN, 1.0f)
    }
}
