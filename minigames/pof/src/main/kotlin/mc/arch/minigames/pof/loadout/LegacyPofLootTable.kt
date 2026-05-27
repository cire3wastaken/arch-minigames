package mc.arch.minigames.pof.loadout

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XPotion
import org.bukkit.inventory.ItemStack

object LegacyPofLootTable : PofLootTable()
{
    override val commonSpec: List<Pair<XMaterial, Int>> = listOf(
        XMaterial.OAK_PLANKS to 8,
        XMaterial.SPRUCE_PLANKS to 8,
        XMaterial.BIRCH_PLANKS to 8,
        XMaterial.JUNGLE_PLANKS to 8,
        XMaterial.COBBLESTONE to 12,
        XMaterial.STONE to 8,
        XMaterial.DIRT to 16,
        XMaterial.SAND to 8,
        XMaterial.GRAVEL to 6,
        XMaterial.NETHERRACK to 16,
        XMaterial.WOODEN_SWORD to 1,
        XMaterial.STONE_SWORD to 1,
        XMaterial.WOODEN_AXE to 1,
        XMaterial.WOODEN_PICKAXE to 1,
        XMaterial.STONE_PICKAXE to 1,
        XMaterial.ARROW to 4,
        XMaterial.APPLE to 2,
        XMaterial.BREAD to 3,
        XMaterial.COOKED_BEEF to 2,
        XMaterial.LADDER to 6,
        XMaterial.STICK to 4,
        XMaterial.LEATHER_HELMET to 1,
        XMaterial.LEATHER_CHESTPLATE to 1,
        XMaterial.LEATHER_LEGGINGS to 1,
        XMaterial.LEATHER_BOOTS to 1,
        XMaterial.COBWEB to 1
    )

    override val uncommonSpec: List<Pair<XMaterial, Int>> = listOf(
        XMaterial.IRON_SWORD to 1,
        XMaterial.IRON_AXE to 1,
        XMaterial.IRON_PICKAXE to 1,
        XMaterial.BOW to 1,
        XMaterial.ARROW to 8,
        XMaterial.FISHING_ROD to 1,
        XMaterial.OBSIDIAN to 4,
        XMaterial.IRON_BLOCK to 1,
        XMaterial.WHITE_WOOL to 16,
        XMaterial.RED_WOOL to 8,
        XMaterial.BLUE_WOOL to 8,
        XMaterial.GLASS to 8,
        XMaterial.IRON_HELMET to 1,
        XMaterial.IRON_CHESTPLATE to 1,
        XMaterial.IRON_LEGGINGS to 1,
        XMaterial.IRON_BOOTS to 1,
        XMaterial.GOLDEN_SWORD to 1,
        XMaterial.GOLDEN_APPLE to 1,
        XMaterial.MUSHROOM_STEW to 1,
        XMaterial.ENDER_PEARL to 1,
        XMaterial.WATER_BUCKET to 1,
        XMaterial.COBWEB to 2
    )

    override val uncommonExtras: List<ItemStack> = listOf(
        knockbackStick(1),
        splashPotion(XPotion.POISON, durationTicks = 200, level = 0),
        splashPotion(XPotion.WEAKNESS, durationTicks = 300, level = 0)
    )

    override val rareSpec: List<Pair<XMaterial, Int>> = listOf(
        XMaterial.DIAMOND_SWORD to 1,
        XMaterial.DIAMOND_AXE to 1,
        XMaterial.DIAMOND_PICKAXE to 1,
        XMaterial.DIAMOND_HELMET to 1,
        XMaterial.DIAMOND_CHESTPLATE to 1,
        XMaterial.DIAMOND_LEGGINGS to 1,
        XMaterial.DIAMOND_BOOTS to 1,
        XMaterial.ENCHANTED_GOLDEN_APPLE to 1,
        XMaterial.ENDER_PEARL to 3,
        XMaterial.GOLDEN_APPLE to 2,
        XMaterial.TNT to 2,
        XMaterial.FLINT_AND_STEEL to 1,
        XMaterial.BOW to 1,
        XMaterial.ARROW to 16,
        XMaterial.OBSIDIAN to 8,
        XMaterial.LAVA_BUCKET to 1,
        XMaterial.EGG to 2,
        XMaterial.SNOWBALL to 4
    )

    override val rareExtras: List<ItemStack> = listOf(
        knockbackStick(2),
        splashPotion(XPotion.SPEED, durationTicks = 600, level = 1),
        splashPotion(XPotion.INSTANT_DAMAGE, durationTicks = 1, level = 1)
    )

    override val junkSpec: List<Pair<XMaterial, Int>> = listOf(
        XMaterial.PAPER to 4,
        XMaterial.STICK to 8,
        XMaterial.STRING to 4,
        XMaterial.FEATHER to 4,
        XMaterial.BONE to 4,
        XMaterial.ROTTEN_FLESH to 4,
        XMaterial.SPIDER_EYE to 1,
        XMaterial.LEATHER to 4,
        XMaterial.BRICK to 4,
        XMaterial.CLAY_BALL to 4,
        XMaterial.FLOWER_POT to 1,
        XMaterial.WHEAT_SEEDS to 4,
        XMaterial.PUMPKIN_SEEDS to 4,
        XMaterial.MELON_SEEDS to 4,
        XMaterial.BLAZE_POWDER to 1,
        XMaterial.BLAZE_ROD to 1,
        XMaterial.GHAST_TEAR to 1,
        XMaterial.GUNPOWDER to 1,
        XMaterial.SUGAR to 4,
        XMaterial.GLASS_BOTTLE to 1,
        XMaterial.BOWL to 4,
        XMaterial.RABBIT_FOOT to 1,
        XMaterial.RABBIT_HIDE to 4,
        XMaterial.NAME_TAG to 1,
        XMaterial.LEAD to 1,
        XMaterial.SADDLE to 1,
        XMaterial.BOOK to 4,
        XMaterial.MAP to 1,
        XMaterial.COMPASS to 1,
        XMaterial.CLOCK to 1,
        XMaterial.MUSIC_DISC_13 to 1,
        XMaterial.MUSIC_DISC_CAT to 1
    )
}
