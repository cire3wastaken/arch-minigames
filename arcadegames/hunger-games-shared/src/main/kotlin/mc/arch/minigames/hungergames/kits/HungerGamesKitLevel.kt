package mc.arch.minigames.hungergames.kits

import org.bukkit.inventory.ItemStack

/**
 * @author ArchMC
 */
data class HungerGamesKitLevel(
    val level: Int,
    var price: Long = 0L,
    var armor: Array<ItemStack?> = arrayOfNulls(4),
    var inventory: Array<ItemStack?> = arrayOfNulls(36)
)
{
    override fun equals(other: Any?): Boolean
    {
        if (this === other) return true
        if (other !is HungerGamesKitLevel) return false
        return level == other.level
    }

    override fun hashCode() = level
}
