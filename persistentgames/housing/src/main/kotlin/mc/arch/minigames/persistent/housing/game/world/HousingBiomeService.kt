package mc.arch.minigames.persistent.housing.game.world

import mc.arch.minigames.persistent.housing.api.content.HousingBiome
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import net.evilblock.cubed.util.bukkit.Tasks
import org.bukkit.World

object HousingBiomeService
{
    /**
     * Applies [house]'s configured biome to every column inside its region
     * and refreshes the affected chunks so clients see the change.
     *
     * No-op when the house has no biome override or no region.
     */
    fun applyConfiguredBiome(house: PlayerHouse, world: World)
    {
        val biome = house.housingBiome?.bukkit ?: return
        val region = house.region ?: return

        Tasks.sync {
            for (x in region.lowerX..region.upperX)
            {
                for (z in region.lowerZ..region.upperZ)
                {
                    world.setBiome(x, z, biome)
                }
            }

            val chunkMinX = region.lowerX shr 4
            val chunkMaxX = region.upperX shr 4
            val chunkMinZ = region.lowerZ shr 4
            val chunkMaxZ = region.upperZ shr 4

            for (cx in chunkMinX..chunkMaxX)
            {
                for (cz in chunkMinZ..chunkMaxZ)
                {
                    @Suppress("DEPRECATION")
                    world.refreshChunk(cx, cz)
                }
            }
        }
    }

    /**
     * Apply the given [biome] to the house's region, persist the choice on
     * [house], and refresh the affected chunks. Returns the number of columns
     * updated, or `null` when the house has no region (not yet bootstrapped).
     */
    fun setBiome(house: PlayerHouse, biome: HousingBiome, world: World): Int?
    {
        val region = house.region ?: return null

        house.housingBiome = biome
        house.save()

        var columns = 0
        for (x in region.lowerX..region.upperX)
        {
            for (z in region.lowerZ..region.upperZ)
            {
                world.setBiome(x, z, biome.bukkit)
                columns++
            }
        }

        val chunkMinX = region.lowerX shr 4
        val chunkMaxX = region.upperX shr 4
        val chunkMinZ = region.lowerZ shr 4
        val chunkMaxZ = region.upperZ shr 4

        for (cx in chunkMinX..chunkMaxX)
        {
            for (cz in chunkMinZ..chunkMaxZ)
            {
                @Suppress("DEPRECATION")
                world.refreshChunk(cx, cz)
            }
        }

        return columns
    }
}
