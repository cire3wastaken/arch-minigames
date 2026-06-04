package mc.arch.minigames.arcade.rlgl

import org.bukkit.entity.Player

/**
 * The base "Red Light, Green Light" loadout: an empty, clean racing state.
 *
 * Players start with nothing and acquire items from ground loot ([RlglLootTable]). Applied at
 * round start and on respawn to wipe any carried-over items, debuffs, or damage from the previous
 * round.
 */
object RlglLoadout
{
    fun apply(player: Player)
    {
        val inventory = player.inventory
        inventory.clear()
        inventory.armorContents = arrayOfNulls(4)
        player.activePotionEffects.toList().forEach { player.removePotionEffect(it.type) }

        player.health = player.maxHealth
        player.foodLevel = 20
        player.saturation = 20f
        player.fireTicks = 0
        player.exp = 0f
        player.level = 0

        player.updateInventory()
    }
}
