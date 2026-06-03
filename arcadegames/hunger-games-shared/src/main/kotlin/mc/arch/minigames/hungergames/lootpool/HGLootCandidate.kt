package mc.arch.minigames.hungergames.lootpool

import com.cryptomorin.xseries.XMaterial
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.inventory.ItemStack

/**
 * @author ArchMC
 */
data class HGLootCandidate(
    var weight: Double = 100.0,
    var amountRangeMin: Int = 1,
    var amountRangeMax: Int = 1,
    var item: ItemStack? = XMaterial.IRON_SWORD.parseItem()
)
{
    fun toItem(): ItemStack = ItemBuilder
        .copyOf(item ?: ItemBuilder.of(XMaterial.BARRIER).build())
        .amount((amountRangeMin..amountRangeMax).random())
        .build()
}
