package mc.arch.minigames.arcade.oitc

import com.cryptomorin.xseries.XMaterial
import org.bukkit.entity.Player

/**
 * The base "One in the Chamber" loadout: a melee sword, a bow, and a single arrow (slot 8).
 *
 * Applied on (re)spawn. Killing a player refills the arrow in slot 8 — see
 * [OitcArcadeLifecycle.handleKill].
 */
object OitcLoadout
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

        XMaterial.IRON_SWORD.parseItem()?.let { inventory.setItem(0, it) }
        XMaterial.BOW.parseItem()?.let { inventory.setItem(1, it) }
        XMaterial.ARROW.parseItem()?.apply { amount = 1 }?.let { inventory.setItem(8, it) }

        player.updateInventory()
    }
}
