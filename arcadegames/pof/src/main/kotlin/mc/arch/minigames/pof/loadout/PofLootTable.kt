package mc.arch.minigames.pof.loadout

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import java.util.concurrent.ThreadLocalRandom

abstract class PofLootTable
{
    protected open val tierCommon = 45
    protected open val tierUncommon = 20
    protected open val tierJunk = 10
    protected open val tierRare = 25

    protected abstract val commonSpec: List<Pair<XMaterial, Int>>
    protected abstract val uncommonSpec: List<Pair<XMaterial, Int>>
    protected abstract val rareSpec: List<Pair<XMaterial, Int>>
    protected abstract val junkSpec: List<Pair<XMaterial, Int>>

    protected open val uncommonExtras: List<ItemStack> = emptyList()
    protected open val rareExtras: List<ItemStack> = emptyList()

    private val common: List<ItemStack> by lazy { build(commonSpec) }
    private val uncommon: List<ItemStack> by lazy { build(uncommonSpec) + uncommonExtras }
    private val rare: List<ItemStack> by lazy { build(rareSpec) + rareExtras }
    private val junk: List<ItemStack> by lazy { build(junkSpec) }
    private val blocks: List<ItemStack> by lazy { common.filter { it.type.isBlock } }

    fun roll(rareUnlocked: Boolean = true): ItemStack
    {
        val random = ThreadLocalRandom.current()
        val pool = when (random.nextInt(tierCommon + tierUncommon + tierJunk + tierRare))
        {
            in 0 until tierCommon -> common
            in tierCommon until tierCommon + tierUncommon -> uncommon
            in tierCommon + tierUncommon until tierCommon + tierUncommon + tierJunk -> junk
            else -> if (rareUnlocked) rare else uncommon
        }

        if (pool.isEmpty()) return ItemStack(Material.STICK)
        return pool.random().clone().also(::applyRollAdjustments)
    }

    fun rollBlock(): ItemStack
    {
        if (blocks.isEmpty()) return ItemStack(Material.COBBLESTONE, 4)
        return blocks.random().clone()
    }

    private fun applyRollAdjustments(item: ItemStack)
    {
        if (item.type == Material.FISHING_ROD)
        {
            val usesRemaining = ThreadLocalRandom.current().nextInt(1, 3)
            item.durability = (item.type.maxDurability - usesRemaining).toShort()
        }
    }

    protected fun build(spec: List<Pair<XMaterial, Int>>): List<ItemStack> = spec
        .mapNotNull { (mat, amount) ->
            val item = mat.parseItem() ?: return@mapNotNull null
            item.amount = amount.coerceAtLeast(1)
            item
        }

    protected fun knockbackStick(level: Int): ItemStack = ItemStack(Material.STICK).apply {
        val meta = itemMeta ?: return@apply
        meta.addEnchant(Enchantment.KNOCKBACK, level, true)
        itemMeta = meta
    }

    protected fun splashPotion(potion: XPotion, durationTicks: Int, level: Int): ItemStack
    {
        val item = XMaterial.SPLASH_POTION.parseItem() ?: return ItemStack(Material.STICK)
        val meta = item.itemMeta as? PotionMeta ?: return item
        val effectType = potion.potionEffectType ?: return item
        meta.addCustomEffect(PotionEffect(effectType, durationTicks, level), true)
        item.itemMeta = meta
        return item
    }
}
