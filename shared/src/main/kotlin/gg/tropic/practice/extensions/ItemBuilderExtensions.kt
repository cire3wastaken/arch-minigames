package gg.tropic.practice.extensions

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.versioned.Versioned
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack

fun ItemBuilder.unbreakable(): ItemBuilder =
    ItemBuilder.copyOf(Versioned.toProvider().getItemStackProvider().makeUnbreakable(this.build()))

fun ItemStack?.ensureItemMeta(): ItemStack
{
    if (this == null)
    {
        return ItemBuilder
            .of(XMaterial.IRON_SWORD)
            .build()
    }

    if (this.itemMeta != null)
    {
        return this
    }

    val copy = this.clone()
    val meta = Bukkit.getItemFactory().getItemMeta(copy.type)
        ?: return copy

    copy.itemMeta = meta
    return copy
}
