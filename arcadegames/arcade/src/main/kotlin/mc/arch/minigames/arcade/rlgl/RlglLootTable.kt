package mc.arch.minigames.arcade.rlgl

import com.cryptomorin.xseries.XEnchantment
import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import java.util.concurrent.ThreadLocalRandom

object RlglLootTable
{
    private const val WEIGHT_DEBUFF_POTION = 45
    private const val WEIGHT_FREEZE_WAND = 15
    private const val WEIGHT_SPEED_BOOTS = 12
    private const val WEIGHT_SABOTAGE_STICK = 8
    private const val WEIGHT_ENDER_PEARL = 10
    private const val WEIGHT_SLOW_FALLING = 12
    private const val TOTAL = WEIGHT_DEBUFF_POTION + WEIGHT_FREEZE_WAND + WEIGHT_SPEED_BOOTS +
        WEIGHT_SABOTAGE_STICK + WEIGHT_ENDER_PEARL + WEIGHT_SLOW_FALLING

    const val FREEZE_WAND_DISPLAY_NAME = "§bFreeze Wand"

    private val fallback: ItemStack by lazy { XMaterial.STICK.parseItem()!! }

    private val debuffs: List<Triple<XPotion, Int, String>> = listOf(
        Triple(XPotion.SLOWNESS, 20 * 10, "§dCrawler Brew"),
        Triple(XPotion.WEAKNESS, 20 * 12, "§dLimp Tonic"),
        Triple(XPotion.BLINDNESS, 20 * 6, "§dBlackout Fizz"),
        Triple(XPotion.POISON, 20 * 6, "§dGreen Goo")
    )

    fun roll(): ItemStack
    {
        var r = ThreadLocalRandom.current().nextInt(TOTAL)
        if (r < WEIGHT_DEBUFF_POTION) return debuffPotion()
        r -= WEIGHT_DEBUFF_POTION
        if (r < WEIGHT_FREEZE_WAND) return freezeWand()
        r -= WEIGHT_FREEZE_WAND
        if (r < WEIGHT_SPEED_BOOTS) return speedBoots()
        r -= WEIGHT_SPEED_BOOTS
        if (r < WEIGHT_SABOTAGE_STICK) return sabotageStick()
        r -= WEIGHT_SABOTAGE_STICK
        if (r < WEIGHT_ENDER_PEARL) return XMaterial.ENDER_PEARL.parseItem()?.apply { amount = 1 } ?: fallback.clone()
        return slowFallingPotion()
    }

    private fun freezeWand(): ItemStack
    {
        val item = XMaterial.PRISMARINE_CRYSTALS.parseItem() ?: return fallback.clone()
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(FREEZE_WAND_DISPLAY_NAME)
        item.itemMeta = meta
        return item
    }

    private fun debuffPotion(): ItemStack
    {
        val (xPotion, ticks, label) = debuffs.random()
        val item = XMaterial.SPLASH_POTION.parseItem() ?: return fallback.clone()
        val meta = item.itemMeta as? PotionMeta ?: return item
        val type = xPotion.potionEffectType ?: return item
        meta.addCustomEffect(PotionEffect(type, ticks, 0), true)
        meta.setDisplayName(label)
        item.itemMeta = meta
        return item
    }

    private fun speedBoots(): ItemStack
    {
        val item = XMaterial.LEATHER_BOOTS.parseItem() ?: return fallback.clone()
        val meta = item.itemMeta ?: return item
        meta.setDisplayName("§aSprint Boots")
        item.itemMeta = meta
        return item
    }

    private fun sabotageStick(): ItemStack
    {
        val item = XMaterial.STICK.parseItem() ?: return fallback.clone()
        val meta = item.itemMeta ?: return item
        XEnchantment.KNOCKBACK.get()?.let { meta.addEnchant(it, 1, true) }
        XEnchantment.WIND_BURST.get()?.let { meta.addEnchant(it, 1, true) }
        meta.setDisplayName("§6Sabotage Stick")
        item.itemMeta = meta
        return item
    }

    private fun slowFallingPotion(): ItemStack
    {
        val item = XMaterial.SPLASH_POTION.parseItem() ?: return fallback.clone()
        val meta = item.itemMeta as? PotionMeta ?: return item
        val type = XPotion.SLOW_FALLING.potionEffectType ?: return item
        meta.addCustomEffect(PotionEffect(type, 20 * 20, 0), true)
        meta.setDisplayName("§bAirbag Potion")
        item.itemMeta = meta
        return item
    }

    fun sprintEffect(): PotionEffect
    {
        val type = XPotion.SPEED.potionEffectType!!
        return PotionEffect(type, 20 * 30, 1, true, false)
    }
}
