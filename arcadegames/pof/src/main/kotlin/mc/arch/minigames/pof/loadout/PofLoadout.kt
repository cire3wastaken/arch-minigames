package mc.arch.minigames.pof.loadout

import org.bukkit.entity.Player

object PofLoadout
{
    fun apply(player: Player)
    {
        player.inventory.clear()
        player.inventory.armorContents = arrayOfNulls(4)
        player.health = player.maxHealth
        player.foodLevel = 20
        player.fireTicks = 0
        player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
        player.updateInventory()
    }
}
