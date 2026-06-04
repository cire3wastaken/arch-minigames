package mc.arch.minigames.hungergames.game

import me.lucko.helper.Schedulers
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import java.util.*

/**
 * Represents a glass cage built around a spawn location
 * to contain players during the pre-game waiting phase.
 *
 * The player stands on a 2-high platform. The cage surrounds
 * the 1x2 area at the player's feet (Y) and head (Y+1) level,
 * with a glass ceiling at Y+2.
 *
 */
class GlassCage(
    val spawnLocation: Location
)
{
    var occupant: UUID? = null
        private set

    private val cageBlocks = mutableListOf<Block>()

    fun isOccupied() = occupant != null

    fun assign(player: UUID)
    {
        occupant = player
    }

    fun release()
    {
        occupant = null
    }

    /**
     * Builds the glass cage around the spawn location.
     * The spawn location is where the player's feet are.
     *
     * The player already stands on a platform, so we never touch the
     * block under them. We only place the four cardinal walls (direct
     * left/right/front/back) at feet and head level, plus a single
     * ceiling block. No diagonal/corner blocks, no floor.
     *
     * Layout (top-down view, player at P, G = glass, . = untouched):
     * ```
     *  .G.
     *  GPG
     *  .G.
     * ```
     */
    fun build() = onMainThread { doBuild() }

    private fun doBuild()
    {
        val world = spawnLocation.world
        val x = spawnLocation.blockX
        val y = spawnLocation.blockY
        val z = spawnLocation.blockZ

        // Only the 4 cardinal offsets — no diagonals/corners.
        val cardinalOffsets = listOf(
            intArrayOf(0, -1), // front (or back, depending on facing)
            intArrayOf(0, 1),
            intArrayOf(-1, 0), // left
            intArrayOf(1, 0)   // right
        )

        // Walls at feet level (Y) and head level (Y+1)
        for (dy in 0..1)
        {
            for (offset in cardinalOffsets)
            {
                val block = world.getBlockAt(x + offset[0], y + dy, z + offset[1])
                block.type = Material.GLASS
                cageBlocks.add(block)
            }
        }

        // Ceiling at Y+2 (the "top" block)
        val ceiling = world.getBlockAt(x, y + 2, z)
        ceiling.type = Material.GLASS
        cageBlocks.add(ceiling)
    }

    /**
     * Removes all glass blocks placed by this cage,
     * setting them back to AIR.
     */
    fun destroy() = onMainThread {
        for (block in cageBlocks)
        {
            if (block.type == Material.GLASS)
            {
                block.type = Material.AIR
            }
        }
        cageBlocks.clear()
    }

    /**
     * Run [block] on the Bukkit main thread. Block writes hit AsyncCatcher if
     * called off-thread (e.g. from the world-load background thread that
     * drives [HungerGamesLifecycle.configure]).
     */
    private inline fun onMainThread(crossinline block: () -> Unit)
    {
        if (Bukkit.isPrimaryThread()) block()
        else Schedulers.sync().run { block() }
    }
}
