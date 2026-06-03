package mc.arch.minigames.hungergames.kits

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * @author ArchMC
 */
data class HungerGamesKit(
    val id: String,
    var displayName: String = id,
    var icon: ItemStack = XMaterial.IRON_SWORD.parseItem()!!,
    val levels: MutableMap<Int, HungerGamesKitLevel> = mutableMapOf()
)
{
    companion object
    {
        /**
         * The highest level that can be purchased normally.
         * Level 6 (PRESTIGE_LEVEL) is reserved for prestige rewards.
         */
        const val PURCHASABLE_MAX_LEVEL = 5

        /**
         * The level reserved for the prestige loadout.
         */
        const val PRESTIGE_LEVEL = 6
    }

    fun maxLevel() = levels.keys.maxOrNull() ?: 0

    /**
     * Max level available for normal purchase (excludes prestige level).
     */
    fun purchasableMaxLevel() = levels.keys.filter { it <= PURCHASABLE_MAX_LEVEL }.maxOrNull() ?: 0

    fun applyTo(player: Player, level: Int)
    {
        val profile = HungerGamesProfileService.find(player)
        val hasPrestiged = (profile?.getPrestige(id) ?: 0) >= 1

        // If prestiged, use the prestige level (6); otherwise, cap at the purchasable max (5)
        val effectiveLevel = if (hasPrestiged)
        {
            PRESTIGE_LEVEL
        } else
        {
            level.coerceIn(1, PURCHASABLE_MAX_LEVEL)
        }

        val kitLevel = levels[effectiveLevel]
            ?: levels[PURCHASABLE_MAX_LEVEL]
            ?: levels[1]
            ?: return

        // Apply armor (never customizable)
        kitLevel.armor.forEachIndexed { index, item ->
            if (item != null)
            {
                player.inventory.armorContents[index] = item.clone()
            }
        }
        player.inventory.armorContents = player.inventory.armorContents

        // Check for custom loadout
        val customLoadout = profile?.customLoadouts?.get(id)

        val inventoryToApply = customLoadout ?: kitLevel.inventory

        inventoryToApply.forEachIndexed { index, item ->
            if (item != null)
            {
                player.inventory.setItem(index, item.clone())
            }
        }

        player.updateInventory()
    }
}
