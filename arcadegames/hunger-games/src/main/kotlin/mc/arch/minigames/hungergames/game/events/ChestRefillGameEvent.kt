package mc.arch.minigames.hungergames.game.events

import gg.tropic.practice.games.GameService
import gg.tropic.practice.minigame.MiniGameEvent
import mc.arch.minigames.hungergames.game.HungerGamesLifecycle
import mc.arch.minigames.hungergames.lootpool.HGLootGenerator
import mc.arch.minigames.hungergames.lootpool.HGLootType
import net.evilblock.cubed.util.CC
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Chest
import java.time.Duration

/**
 * @author ArchMC
 */
class ChestRefillGameEvent(
    private val lifecycle: HungerGamesLifecycle,
    override val description: String = "Chest Refill",
    override val duration: Duration = Duration.ofSeconds(100)
) : MiniGameEvent
{
    override fun execute()
    {
        val world = lifecycle.game.arenaWorld
        val chests = mutableListOf<Chest>()

        for (chunk in world.loadedChunks)
        {
            for (te in chunk.tileEntities)
            {
                if (te is Chest)
                {
                    te.inventory.clear()
                    chests.add(te)
                }
            }
        }

        HGLootGenerator.fillChestsFromDataSync(chests, HGLootType.REFILL)

        lifecycle.game.sendMessage("${CC.YELLOW}All chests have been ${CC.GREEN}refilled${CC.YELLOW}!")
        lifecycle.game.playSound(Sound.CHEST_OPEN, 1.0f)
    }
}
