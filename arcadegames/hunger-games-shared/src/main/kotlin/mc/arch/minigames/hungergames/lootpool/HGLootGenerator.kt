package mc.arch.minigames.hungergames.lootpool

import org.bukkit.block.Chest
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

/**
 * @author ArchMC
 */
object HGLootGenerator
{
    fun fillChests(
        chests: List<Chest>,
        candidates: List<HGLootCandidate>,
        fillPercentage: Double = 0.6
    )
    {
        if (chests.isEmpty() || candidates.isEmpty()) return

        val emptySlots = getEmptySlots(chests)
        val maxSlotsToFill = (emptySlots.size * fillPercentage).toInt().coerceAtMost(emptySlots.size)

        val itemsToPlace = mutableListOf<ItemStack>()

        candidates.forEach { candidate ->
            val spawnChance = candidate.weight.coerceIn(0.01, 100.0)
            val randomValue = Random.nextDouble(0.0, 100.0)

            if (randomValue <= spawnChance)
            {
                itemsToPlace.add(candidate.toItem())
            }
        }

        val itemsToActuallyPlace = if (itemsToPlace.size > maxSlotsToFill)
        {
            itemsToPlace.shuffled().take(maxSlotsToFill)
        } else
        {
            itemsToPlace
        }

        val selectedSlots = emptySlots.shuffled().take(itemsToActuallyPlace.size)

        itemsToActuallyPlace.forEachIndexed { index, item ->
            val (chestIndex, slotIndex) = selectedSlots[index]
            if (chestIndex < chests.size)
            {
                val chest = chests[chestIndex]
                if (chest.inventory.getItem(slotIndex) == null)
                {
                    chest.inventory.setItem(slotIndex, item)
                }
            }
        }
    }

    private fun getEmptySlots(chests: List<Chest>): List<Pair<Int, Int>>
    {
        val emptySlots = mutableListOf<Pair<Int, Int>>()

        chests.forEachIndexed { chestIndex, chest ->
            for (slotIndex in 0 until chest.inventory.size)
            {
                if (chest.inventory.getItem(slotIndex) == null)
                {
                    emptySlots.add(Pair(chestIndex, slotIndex))
                }
            }
        }

        return emptySlots
    }

    fun fillChestsFromDataSync(
        chests: List<Chest>,
        lootType: HGLootType
    )
    {
        val container = HGLootDataSync.cached()
        val scopeContainer = container.types[lootType] ?: return
        fillChests(chests, scopeContainer.candidates, scopeContainer.fillPercentage)
    }
}
