@file:Suppress("DEPRECATION")

package mc.arch.minigames.hungergames.kits

import com.cryptomorin.xseries.XMaterial
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * Contains all 16 legacy Hunger Games kits with 6 levels each,
 * ported from the Scala.kits.json legacy data.
 *
 * @author ArchMC
 */
object DefaultHungerGamesKits
{
    fun buildDefaultKits(): MutableMap<String, HungerGamesKit>
    {
        val kits = mutableMapOf<String, HungerGamesKit>()

        kits["baker"] = buildBakerKit()
        kits["knight"] = buildKnightKit()
        kits["archer"] = buildArcherKit()
        kits["meatmaster"] = buildMeatmasterKit()
        kits["scout"] = buildScoutKit()
        kits["armorer"] = buildArmorerKit()
        kits["horsetamer"] = buildHorsetamerKit()
        kits["astronaut"] = buildAstronautKit()
        kits["warlock"] = buildWarlockKit()
        kits["slime"] = buildSlimeKit()
        kits["shadow"] = buildShadowKnightKit()
        kits["pigman"] = buildPigmanKit()
        kits["wolftamer"] = buildWolftamerKit()
        kits["blaze"] = buildBlazeKit()
        kits["creeper"] = buildCreeperKit()
        kits["snowman"] = buildSnowmanKit()

        return kits
    }

    // ==============================
    // Utility functions
    // ==============================

    private fun item(material: XMaterial, amount: Int = 1): ItemStack
    {
        val item = material.parseItem() ?: ItemStack(Material.STONE)
        item.amount = amount
        return item
    }

    private fun item(material: XMaterial, amount: Int, enchants: Map<Enchantment, Int>): ItemStack
    {
        val item = item(material, amount)
        enchants.forEach { (ench, level) -> item.addUnsafeEnchantment(ench, level) }
        return item
    }

    private fun leatherArmor(material: XMaterial, color: Color, enchants: Map<Enchantment, Int> = emptyMap()): ItemStack
    {
        val item = item(material)
        val meta = item.itemMeta as? LeatherArmorMeta ?: return item
        meta.color = color
        item.itemMeta = meta
        enchants.forEach { (ench, level) -> item.addUnsafeEnchantment(ench, level) }
        return item
    }

    private fun customPotion(
        name: String,
        amount: Int = 1,
        splash: Boolean = false,
        effects: List<PotionEffect>
    ): ItemStack
    {
        // In 1.8, both regular and splash potions use Material.POTION
        // Splash potions have durability 16384+, regular have 8192+
        @Suppress("DEPRECATION")
        val item = ItemStack(Material.POTION, amount, if (splash) 16384.toShort() else 8192.toShort())
        val meta = item.itemMeta as? PotionMeta ?: return item
        meta.displayName = "§f$name"
        effects.forEach { meta.addCustomEffect(it, true) }
        item.itemMeta = meta
        return item
    }

    private fun spawnEgg(entityTypeId: Short, amount: Int = 1): ItemStack
    {
        // 1.8: Material.MONSTER_EGG (id 383) with durability = entity type id
        @Suppress("DEPRECATION")
        val item = ItemStack(Material.MONSTER_EGG, amount, entityTypeId)
        return item
    }

    // Spawn egg entity type IDs for 1.8
    private const val ENTITY_COW: Short = 92
    private const val ENTITY_HORSE: Short = 100
    private const val ENTITY_WOLF: Short = 95
    private const val ENTITY_BLAZE: Short = 61
    private const val ENTITY_CREEPER: Short = 50
    private const val ENTITY_SLIME: Short = 55
    private const val ENTITY_PIG_ZOMBIE: Short = 57
    private const val ENTITY_PIG: Short = 90
    private const val ENTITY_IRON_GOLEM: Short = 99 // snowman egg (snow golem = 97 but legacy used wolf)

    // Common enchantment references
    private val PROT = Enchantment.PROTECTION_ENVIRONMENTAL
    private val PROJ_PROT = Enchantment.PROTECTION_PROJECTILE
    private val FIRE_PROT = Enchantment.PROTECTION_FIRE
    private val BLAST_PROT = Enchantment.PROTECTION_EXPLOSIONS
    private val FEATHER = Enchantment.PROTECTION_FALL
    private val UNBREAKING = Enchantment.DURABILITY
    private val SHARPNESS = Enchantment.DAMAGE_ALL
    private val KNOCKBACK = Enchantment.KNOCKBACK
    private val FIRE_ASPECT = Enchantment.FIRE_ASPECT
    private val POWER = Enchantment.ARROW_DAMAGE
    private val INFINITY = Enchantment.ARROW_INFINITE
    private val LOOTING = Enchantment.LOOT_BONUS_MOBS
    private val RESPIRATION = Enchantment.OXYGEN

    /**
     * Default price curve for kit levels.
     * Level 1 is free, higher levels cost progressively more.
     */
    private val DEFAULT_LEVEL_PRICES = mapOf(
        1 to 0L,
        2 to 25_000L,
        3 to 100_000L,
        4 to 250_000L,
        5 to 1_000_000L,
        6 to 2_000_000L
    )

    private fun level(
        level: Int,
        armor: Array<ItemStack?>,
        inventory: Array<ItemStack?>,
        price: Long = DEFAULT_LEVEL_PRICES[level] ?: (level * 2_000L)
    ): Pair<Int, HungerGamesKitLevel>
    {
        return level to HungerGamesKitLevel(
            level = level,
            price = price,
            armor = armor,
            inventory = inventory
        )
    }

    // Armor array is [boots, leggings, chestplate, helmet]
    private fun armor(
        boots: ItemStack? = null,
        leggings: ItemStack? = null,
        chestplate: ItemStack? = null,
        helmet: ItemStack? = null
    ) = arrayOf(boots, leggings, chestplate, helmet)

    private fun inv(vararg items: ItemStack?): Array<ItemStack?>
    {
        val arr = arrayOfNulls<ItemStack>(36)
        items.forEachIndexed { i, item -> if (i < 36) arr[i] = item }
        return arr
    }

    // ==============================
    // COLOR CONSTANTS
    // ==============================
    private val BAKER_COLOR = Color.fromRGB(238, 164, 245) // legacy 0xEEA4F5
    private val SCOUT_COLOR = Color.fromRGB(0, 170, 170) // legacy 0x00AAAA
    private val ARMORER_GREEN = Color.fromRGB(0, 170, 0) // legacy 0x00AA00
    private val ARMORER_LEGS = Color.fromRGB(0, 0, 255) // legacy color
    private val ARMORER_BOOTS = Color.fromRGB(128, 128, 0) // legacy color
    private val HORSETAMER_COLOR = Color.fromRGB(92, 95, 7) // legacy 0x5C5F07
    private val WARLOCK_COLOR = Color.fromRGB(64, 30, 57) // legacy 0x401E39
    private val SLIME_COLOR = Color.fromRGB(0, 128, 0) // legacy 0x008000
    private val SHADOW_COLOR = Color.fromRGB(60, 60, 60) // legacy 0x3C3C3C
    private val PIGMAN_COLOR = Color.fromRGB(255, 255, 0) // legacy 0xFFFF00
    private val WOLFTAMER_COLOR = Color.fromRGB(128, 128, 128) // legacy 0x808080
    private val BLAZE_COLOR = Color.fromRGB(255, 165, 0) // legacy 0xFFA500
    private val CREEPER_COLOR = Color.fromRGB(27, 130, 39) // legacy 0x1B8227
    private val SNOWMAN_COLOR = Color.fromRGB(255, 255, 255) // legacy 0xFFFFFF
    private val ARCHER_COLOR = Color.fromRGB(0, 255, 0) // legacy 0x00FF00
    private val ASTRONAUT_COLOR = Color.fromRGB(255, 255, 255) // legacy 0xFFFFFF

    // ==============================
    // BAKER KIT
    // ==============================
    private fun buildBakerKit(): HungerGamesKit
    {
        val icon = item(XMaterial.CAKE)

        fun bakerPotion(amount: Int, durationTicks: Int) = customPotion(
            "Baker's Potion", amount, false,
            listOf(
                PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, 0),
                PotionEffect(PotionEffectType.ABSORPTION, durationTicks, 0)
            )
        )

        return HungerGamesKit(
            id = "baker",
            displayName = "Baker",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Wooden Sword, Leather armor (dyed), Boots w/ Prot I, Bread x12, Cake x3, Baker's Potion x2
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BAKER_COLOR, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        item(XMaterial.BREAD, 12),
                        item(XMaterial.CAKE, 3),
                        bakerPotion(2, 120)
                    )
                ),
                // Level 2: Stone Sword w/ Unbreaking I, Leather armor, Boots w/ Prot II, Bread x16, Cake x3, Potion x2
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BAKER_COLOR, mapOf(PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.BREAD, 16),
                        item(XMaterial.CAKE, 3),
                        bakerPotion(2, 120)
                    )
                ),
                // Level 3: Stone Sword w/ Unbreaking I, Leather armor, Iron Boots, Bread x20, Cake x4, Potion x3
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.BREAD, 20),
                        item(XMaterial.CAKE, 4),
                        bakerPotion(3, 120)
                    )
                ),
                // Level 4: Stone Sword w/ Unbreaking I, Leather armor, Iron Boots w/ Prot I, Bread x24, Cake x5, Golden Apple, Potion x3
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.BREAD, 24),
                        item(XMaterial.CAKE, 5),
                        item(XMaterial.GOLDEN_APPLE),
                        bakerPotion(3, 120)
                    )
                ),
                // Level 5: Iron Sword w/ Unbreaking I, Leather armor, Iron Boots w/ Prot I, Bread x32, Cake x5, Golden Apple, Potion x3
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.BREAD, 32),
                        item(XMaterial.CAKE, 5),
                        item(XMaterial.GOLDEN_APPLE),
                        bakerPotion(3, 120)
                    )
                ),
                // Level 6: Iron Sword w/ Unbreaking I, Leather armor w/ Prot II, Iron Boots w/ Prot II, Bread x32, Cake x5, Golden Apple, Potion x3 (0:08)
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BAKER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BAKER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BAKER_COLOR)
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.BREAD, 32),
                        item(XMaterial.CAKE, 5),
                        item(XMaterial.GOLDEN_APPLE),
                        bakerPotion(3, 160)
                    )
                )
            )
        )
    }

    // ==============================
    // KNIGHT KIT
    // ==============================
    private fun buildKnightKit(): HungerGamesKit
    {
        val icon = item(XMaterial.GOLDEN_SWORD)

        return HungerGamesKit(
            id = "knight",
            displayName = "Knight",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Stone Sword w/ Unbreaking I, Full Golden armor
                level(
                    1,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE),
                        helmet = item(XMaterial.GOLDEN_HELMET)
                    ),
                    inv(item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)))
                ),
                // Level 2: Stone Sword, Full Golden armor
                level(
                    2,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE),
                        helmet = item(XMaterial.GOLDEN_HELMET)
                    ),
                    inv(item(XMaterial.STONE_SWORD))
                ),
                // Level 3: Stone Sword, Full Golden armor w/ Prot I, Golden Carrot
                level(
                    3,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS, 1, mapOf(PROT to 1)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(PROT to 1)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        item(XMaterial.GOLDEN_CARROT)
                    )
                ),
                // Level 4: Stone Sword, Golden armor (varied Protection I-II), Golden Carrot x2, Golden Apple
                level(
                    4,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS, 1, mapOf(PROT to 1)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(PROT to 2)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        item(XMaterial.GOLDEN_CARROT, 2),
                        item(XMaterial.GOLDEN_APPLE)
                    )
                ),
                // Level 5: Iron Sword w/ Unbreaking I, Golden armor (Prot I-II), Golden Carrot x2, Golden Apple
                level(
                    5,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS, 1, mapOf(PROT to 2)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(PROT to 2)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.GOLDEN_CARROT, 2),
                        item(XMaterial.GOLDEN_APPLE)
                    )
                ),
                // Level 6: Iron Sword w/ Unbreaking I, Golden armor (Prot II-III), Golden Carrot x2, Golden Apple
                level(
                    6,
                    armor(
                        boots = item(XMaterial.GOLDEN_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = item(XMaterial.GOLDEN_LEGGINGS, 1, mapOf(PROT to 2)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(PROT to 3)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 2))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.GOLDEN_CARROT, 2),
                        item(XMaterial.GOLDEN_APPLE)
                    )
                )
            )
        )
    }

    // ==============================
    // ARCHER KIT
    // ==============================
    private fun buildArcherKit(): HungerGamesKit
    {
        val icon = item(XMaterial.BOW)

        return HungerGamesKit(
            id = "archer",
            displayName = "Archer",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Bow w/ Power I, Diamond Helmet, Leather Leggings, Leather Boots, Arrow x28
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET)
                    ),
                    inv(
                        item(XMaterial.BOW, 1, mapOf(POWER to 1)),
                        item(XMaterial.ARROW, 28)
                    )
                ),
                // Level 2: Bow w/ Power I, Diamond Helmet w/ Prot I, Leather Leggings, Leather Boots, Arrow x32
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.BOW, 1, mapOf(POWER to 1)),
                        item(XMaterial.ARROW, 32)
                    )
                ),
                // Level 3: Bow w/ Power I, Diamond Helmet w/ Prot II, Leather Chestplate, Leather Leggings, Leather Boots, Arrow x36
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARCHER_COLOR),
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 2))
                    ),
                    inv(
                        item(XMaterial.BOW, 1, mapOf(POWER to 1)),
                        item(XMaterial.ARROW, 36)
                    )
                ),
                // Level 4: Bow w/ Power I, Diamond Helmet w/ Prot III, Leather Chestplate, Leggings, Boots, Arrow x40
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARCHER_COLOR),
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.BOW, 1, mapOf(POWER to 1)),
                        item(XMaterial.ARROW, 40)
                    )
                ),
                // Level 5: Bow w/ Power II, Diamond Helmet w/ Prot III, Leather Chestplate, Leggings, Boots, Arrow x40
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARCHER_COLOR),
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.BOW, 1, mapOf(POWER to 2)),
                        item(XMaterial.ARROW, 40)
                    )
                ),
                // Level 6: Wooden Sword, Bow w/ Power II, Diamond Helmet w/ Prot III, Leather full, Arrow x44
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARCHER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARCHER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARCHER_COLOR),
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        item(XMaterial.BOW, 1, mapOf(POWER to 2)),
                        item(XMaterial.ARROW, 44)
                    )
                )
            )
        )
    }

    // ==============================
    // MEATMASTER KIT
    // ==============================
    private fun buildMeatmasterKit(): HungerGamesKit
    {
        val icon = item(XMaterial.COOKED_BEEF)

        return HungerGamesKit(
            id = "meatmaster",
            displayName = "Meatmaster",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Wooden Sword w/ Looting II, Diamond Helmet, Iron Boots, Cow Egg x3, Steak x12
                level(
                    1,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = null,
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET)
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD, 1, mapOf(LOOTING to 2)),
                        spawnEgg(ENTITY_COW, 3),
                        item(XMaterial.COOKED_BEEF, 12)
                    )
                ),
                // Level 2: Stone Sword w/ Looting II, Diamond Helmet w/ Prot I, Iron Boots, Cow Egg x4, Steak x16
                level(
                    2,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = null,
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(LOOTING to 2)),
                        spawnEgg(ENTITY_COW, 4),
                        item(XMaterial.COOKED_BEEF, 16)
                    )
                ),
                // Level 3: Stone Sword w/ Looting II, Diamond Helmet w/ Prot II, Iron Boots, Cow Egg x4, Steak x20
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = null,
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(LOOTING to 2)),
                        spawnEgg(ENTITY_COW, 4),
                        item(XMaterial.COOKED_BEEF, 20)
                    )
                ),
                // Level 4: Stone Sword w/ Looting III, Diamond Helmet w/ Prot III, Iron Boots, Cow Egg x5, Steak x24
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = null,
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(LOOTING to 3)),
                        spawnEgg(ENTITY_COW, 5),
                        item(XMaterial.COOKED_BEEF, 24)
                    )
                ),
                // Level 5: Iron Sword w/ Looting III, Diamond Helmet w/ Prot IV, Iron Boots, Cow Egg x5, Pig Egg x1, Saddle, Carrot on a Stick, Steak x28
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = null,
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(LOOTING to 3)),
                        spawnEgg(ENTITY_COW, 5),
                        spawnEgg(ENTITY_PIG, 1),
                        item(XMaterial.SADDLE),
                        item(XMaterial.CARROT_ON_A_STICK),
                        item(XMaterial.COOKED_BEEF, 28)
                    )
                ),
                // Level 6: Iron Sword w/ Looting III, Diamond Helmet w/ Prot IV, Leather Leggings (Butcher's Pants), Iron Boots w/ Prot I, Cow Egg x5, Pig Egg x1, Saddle, Carrot on a Stick, Steak x28
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, Color.fromRGB(255, 85, 85)),
                        chestplate = null,
                        helmet = item(XMaterial.DIAMOND_HELMET, 1, mapOf(PROT to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(LOOTING to 3)),
                        spawnEgg(ENTITY_COW, 5),
                        spawnEgg(ENTITY_PIG, 1),
                        item(XMaterial.SADDLE),
                        item(XMaterial.CARROT_ON_A_STICK),
                        item(XMaterial.COOKED_BEEF, 28)
                    )
                )
            )
        )
    }

    // ==============================
    // SCOUT KIT
    // ==============================
    private fun buildScoutKit(): HungerGamesKit
    {
        val icon = item(XMaterial.POTION)

        fun speedPotion(amount: Int, durationTicks: Int) = customPotion(
            "Potion of Swiftness", amount, false,
            listOf(PotionEffect(PotionEffectType.SPEED, durationTicks, 1)) // Speed II
        )

        fun slownessSplash(amount: Int) = customPotion(
            "Splash Potion of Slowness", amount, true,
            listOf(PotionEffect(PotionEffectType.SLOW, 160, 0)) // 0:08
        )

        fun regenPotion(amount: Int) = customPotion(
            "Potion of Regeneration", amount, false,
            listOf(PotionEffect(PotionEffectType.REGENERATION, 200, 0)) // 0:10
        )

        return HungerGamesKit(
            id = "scout",
            displayName = "Scout",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Full Leather armor (dyed), Speed II x3 (0:16), Slowness splash x2
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR)
                    ),
                    inv(
                        speedPotion(3, 320),
                        slownessSplash(2)
                    )
                ),
                // Level 2: Leather Helmet w/ Respiration II, rest leather, Speed II x3 (0:17), Slowness x2, Regen x1
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR, mapOf(RESPIRATION to 2))
                    ),
                    inv(
                        speedPotion(3, 340),
                        slownessSplash(2),
                        regenPotion(1)
                    )
                ),
                // Level 3: Same as 2 but Speed x3 (0:18)
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR, mapOf(RESPIRATION to 2))
                    ),
                    inv(
                        speedPotion(3, 360),
                        slownessSplash(2),
                        regenPotion(1)
                    )
                ),
                // Level 4: Speed x4 (0:20), Slowness x3, Regen x1
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR, mapOf(RESPIRATION to 2))
                    ),
                    inv(
                        speedPotion(4, 400),
                        slownessSplash(3),
                        regenPotion(1)
                    )
                ),
                // Level 5: Speed x5 (0:20), Slowness x3, Regen x2
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR, mapOf(RESPIRATION to 2))
                    ),
                    inv(
                        speedPotion(5, 400),
                        slownessSplash(3),
                        regenPotion(2)
                    )
                ),
                // Level 6: Wooden Axe, Speed x5 (0:23), Slowness x3, Regen x2
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SCOUT_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SCOUT_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SCOUT_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, SCOUT_COLOR, mapOf(RESPIRATION to 2))
                    ),
                    inv(
                        item(XMaterial.WOODEN_AXE),
                        speedPotion(5, 460),
                        slownessSplash(3),
                        regenPotion(2)
                    )
                )
            )
        )
    }

    // ==============================
    // ARMORER KIT
    // ==============================
    private fun buildArmorerKit(): HungerGamesKit
    {
        val icon = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARMORER_GREEN)

        return HungerGamesKit(
            id = "armorer",
            displayName = "Armorer",
            icon = icon,
            levels = mutableMapOf(
                // Level 1: Full leather w/ heavy enchants, Cookie x6
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 1, FEATHER to 3)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ARMORER_LEGS, mapOf(PROT to 2, FIRE_PROT to 3)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, ARMORER_GREEN, mapOf(PROT to 3, BLAST_PROT to 3)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, Color.fromRGB(255, 165, 0), mapOf(PROT to 2, PROJ_PROT to 2))
                    ),
                    inv(item(XMaterial.COOKIE, 6))
                ),
                // Level 2: Same but stronger, Cookie x7
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 2, FEATHER to 3, UNBREAKING to 1)),
                        leggings = leatherArmor(
                            XMaterial.LEATHER_LEGGINGS,
                            ARMORER_LEGS,
                            mapOf(PROT to 3, FIRE_PROT to 4, UNBREAKING to 1)
                        ),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            ARMORER_GREEN,
                            mapOf(PROT to 3, BLAST_PROT to 4, UNBREAKING to 1)
                        ),
                        helmet = leatherArmor(
                            XMaterial.LEATHER_HELMET,
                            Color.fromRGB(255, 165, 0),
                            mapOf(PROT to 2, PROJ_PROT to 3, UNBREAKING to 1)
                        )
                    ),
                    inv(item(XMaterial.COOKIE, 7))
                ),
                // Level 3
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 2, FEATHER to 3, UNBREAKING to 1)),
                        leggings = leatherArmor(
                            XMaterial.LEATHER_LEGGINGS,
                            ARMORER_LEGS,
                            mapOf(PROT to 3, FIRE_PROT to 5, UNBREAKING to 1)
                        ),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            ARMORER_GREEN,
                            mapOf(PROT to 3, BLAST_PROT to 5, UNBREAKING to 1)
                        ),
                        helmet = leatherArmor(
                            XMaterial.LEATHER_HELMET,
                            Color.fromRGB(255, 165, 0),
                            mapOf(PROT to 2, PROJ_PROT to 4, UNBREAKING to 1)
                        )
                    ),
                    inv(item(XMaterial.COOKIE, 8))
                ),
                // Level 4
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 2, FEATHER to 3, UNBREAKING to 2)),
                        leggings = leatherArmor(
                            XMaterial.LEATHER_LEGGINGS,
                            ARMORER_LEGS,
                            mapOf(PROT to 4, FIRE_PROT to 5, UNBREAKING to 2)
                        ),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            ARMORER_GREEN,
                            mapOf(PROT to 4, BLAST_PROT to 5, UNBREAKING to 2)
                        ),
                        helmet = leatherArmor(
                            XMaterial.LEATHER_HELMET,
                            Color.fromRGB(255, 165, 0),
                            mapOf(PROT to 2, PROJ_PROT to 5, UNBREAKING to 2)
                        )
                    ),
                    inv(item(XMaterial.COOKIE, 9))
                ),
                // Level 5
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 2, FEATHER to 4, UNBREAKING to 2)),
                        leggings = leatherArmor(
                            XMaterial.LEATHER_LEGGINGS,
                            ARMORER_LEGS,
                            mapOf(PROT to 4, FIRE_PROT to 10, UNBREAKING to 2)
                        ),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            ARMORER_GREEN,
                            mapOf(PROT to 4, BLAST_PROT to 10, UNBREAKING to 2)
                        ),
                        helmet = leatherArmor(
                            XMaterial.LEATHER_HELMET,
                            Color.fromRGB(255, 165, 0),
                            mapOf(PROT to 2, PROJ_PROT to 10, UNBREAKING to 2)
                        )
                    ),
                    inv(item(XMaterial.COOKIE, 10))
                ),
                // Level 6: Wooden Sword w/ Unbreaking III + all armor w/ Unbreaking III
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, ARMORER_BOOTS, mapOf(PROT to 2, FEATHER to 10, UNBREAKING to 3)),
                        leggings = leatherArmor(
                            XMaterial.LEATHER_LEGGINGS,
                            ARMORER_LEGS,
                            mapOf(PROT to 4, FIRE_PROT to 10, UNBREAKING to 3)
                        ),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            ARMORER_GREEN,
                            mapOf(PROT to 4, BLAST_PROT to 10, UNBREAKING to 3)
                        ),
                        helmet = leatherArmor(
                            XMaterial.LEATHER_HELMET,
                            Color.fromRGB(255, 165, 0),
                            mapOf(PROT to 2, PROJ_PROT to 10, UNBREAKING to 3)
                        )
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD, 1, mapOf(UNBREAKING to 3)),
                        item(XMaterial.COOKIE, 10)
                    )
                )
            )
        )
    }

    // ==============================
    // HORSETAMER KIT
    // ==============================
    private fun buildHorsetamerKit(): HungerGamesKit
    {
        val icon = item(XMaterial.SADDLE)

        return HungerGamesKit(
            id = "horsetamer",
            displayName = "Horsetamer",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, HORSETAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.GOLDEN_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 3)
                    ),
                    price = 150_000L
                ),
                level(
                    2,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, HORSETAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.IRON_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 4)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, HORSETAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.IRON_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 4)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, HORSETAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR, mapOf(PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.DIAMOND_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 6)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, HORSETAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR, mapOf(PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.DIAMOND_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 8)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, HORSETAMER_COLOR, mapOf(PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, HORSETAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE, 1, mapOf(INFINITY to 1)),
                        spawnEgg(ENTITY_HORSE, 1),
                        item(XMaterial.DIAMOND_HORSE_ARMOR),
                        item(XMaterial.SADDLE),
                        item(XMaterial.APPLE, 10)
                    )
                )
            )
        )
    }

    // ==============================
    // ASTRONAUT KIT
    // ==============================
    private fun buildAstronautKit(): HungerGamesKit
    {
        val icon = item(XMaterial.IRON_BOOTS)

        fun resistPotion(amount: Int, durationTicks: Int) = customPotion(
            "Potion of Resistance", amount, false,
            listOf(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, durationTicks, 0))
        )

        return HungerGamesKit(
            id = "astronaut",
            displayName = "Astronaut",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = item(XMaterial.CHAINMAIL_BOOTS, 1, mapOf(FEATHER to 5)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ASTRONAUT_COLOR),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.STONE_AXE),
                        resistPotion(2, 240)
                    ),
                    price = 30_000L
                ),
                level(
                    2,
                    armor(
                        boots = item(XMaterial.CHAINMAIL_BOOTS, 1, mapOf(FEATHER to 5)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ASTRONAUT_COLOR),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        resistPotion(2, 240)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(FEATHER to 6)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ASTRONAUT_COLOR),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, ASTRONAUT_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        resistPotion(2, 240)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(FEATHER to 6, PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ASTRONAUT_COLOR),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = item(XMaterial.CHAINMAIL_HELMET, 1, mapOf(BLAST_PROT to 3))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        resistPotion(3, 260)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(FEATHER to 10, PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, ASTRONAUT_COLOR),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = item(XMaterial.CHAINMAIL_HELMET, 1, mapOf(BLAST_PROT to 3))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        resistPotion(4, 280)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(FEATHER to 10, PROT to 2)),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS, 1, mapOf(PROT to 1)),
                        chestplate = item(XMaterial.CHAINMAIL_CHESTPLATE),
                        helmet = item(XMaterial.CHAINMAIL_HELMET, 1, mapOf(BLAST_PROT to 3))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        resistPotion(4, 320)
                    )
                )
            )
        )
    }

    // ==============================
    // WARLOCK KIT
    // ==============================
    private fun buildWarlockKit(): HungerGamesKit
    {
        val icon = item(XMaterial.POTION)

        fun warlockPotion(amount: Int) = customPotion(
            "Warlock's Potion", amount, false,
            listOf(
                PotionEffect(PotionEffectType.INCREASE_DAMAGE, 140, 0), // Strength I 0:07
                PotionEffect(PotionEffectType.SLOW, 140, 0) // Slowness I 0:07
            )
        )

        return HungerGamesKit(
            id = "warlock",
            displayName = "Warlock",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, WARLOCK_COLOR, mapOf(PROT to 1)),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS, 1, mapOf(PROT to 2, UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD, 1, mapOf(UNBREAKING to 10)),
                        warlockPotion(2)
                    ),
                    price = 20_000L
                ),
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, WARLOCK_COLOR, mapOf(PROT to 2)),
                        leggings = item(XMaterial.IRON_LEGGINGS, 1, mapOf(UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WARLOCK_COLOR)
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD, 1, mapOf(UNBREAKING to 10)),
                        warlockPotion(2)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = item(XMaterial.IRON_LEGGINGS, 1, mapOf(PROT to 1, UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WARLOCK_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 10)),
                        warlockPotion(2)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = item(XMaterial.IRON_LEGGINGS, 1, mapOf(PROT to 2, UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WARLOCK_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 10)),
                        warlockPotion(2)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = item(XMaterial.DIAMOND_LEGGINGS, 1, mapOf(UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WARLOCK_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 10)),
                        warlockPotion(3)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = item(XMaterial.DIAMOND_LEGGINGS, 1, mapOf(PROT to 1, UNBREAKING to 10)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WARLOCK_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WARLOCK_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        warlockPotion(3)
                    )
                )
            )
        )
    }

    // ==============================
    // SLIME KIT (SlimeySlime)
    // ==============================
    private fun buildSlimeKit(): HungerGamesKit
    {
        val icon = item(XMaterial.SLIME_BALL)

        fun slownessSplash(amount: Int) = customPotion(
            "Splash Potion of Slowness", amount, true,
            listOf(PotionEffect(PotionEffectType.SLOW, 160, 0))
        )

        return HungerGamesKit(
            id = "slime",
            displayName = "SlimeySlime",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 4)),
                        leggings = null,
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 4))
                    ),
                    inv(
                        item(XMaterial.STONE_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 2),
                        slownessSplash(2)
                    ),
                    price = 20_000L
                ),
                level(
                    2,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 4)),
                        leggings = null,
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 3),
                        slownessSplash(2)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 4)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SLIME_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 3),
                        slownessSplash(3)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 4, FEATHER to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SLIME_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 4),
                        slownessSplash(4)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 4, FEATHER to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SLIME_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 4))
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 4),
                        slownessSplash(4)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 10, FEATHER to 4)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SLIME_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SLIME_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROJ_PROT to 10))
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE, 1, mapOf(KNOCKBACK to 1)),
                        spawnEgg(ENTITY_SLIME, 4),
                        slownessSplash(4)
                    )
                )
            )
        )
    }

    // ==============================
    // SHADOW KNIGHT KIT
    // ==============================
    private fun buildShadowKnightKit(): HungerGamesKit
    {
        val icon = item(XMaterial.WITHER_SKELETON_SKULL)

        fun blindnessSplash(amount: Int) = customPotion(
            "Splash Potion of Blindness", amount, true,
            listOf(PotionEffect(PotionEffectType.BLINDNESS, 120, 0)) // 0:06
        )

        return HungerGamesKit(
            id = "shadow",
            displayName = "Shadow Knight",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 3, FEATHER to 2))
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        blindnessSplash(1)
                    ),
                    price = 15_000L
                ),
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 3, FEATHER to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        blindnessSplash(2)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 3, FEATHER to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        blindnessSplash(3)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 4, FEATHER to 3))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        blindnessSplash(3)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 5, FEATHER to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        blindnessSplash(3)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, SHADOW_COLOR),
                        leggings = item(XMaterial.CHAINMAIL_LEGGINGS),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SHADOW_COLOR),
                        helmet = item(XMaterial.WITHER_SKELETON_SKULL, 1, mapOf(PROT to 5, FEATHER to 4))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        blindnessSplash(4)
                    )
                )
            )
        )
    }

    // ==============================
    // PIGMAN KIT
    // ==============================
    private fun buildPigmanKit(): HungerGamesKit
    {
        val icon = item(XMaterial.COOKED_PORKCHOP)

        fun harmingSplash(amount: Int) = customPotion(
            "Splash Potion of Harming", amount, true,
            listOf(PotionEffect(PotionEffectType.HARM, 1, 0))
        )

        return HungerGamesKit(
            id = "pigman",
            displayName = "Pigman",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 1, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(2)
                    ),
                    price = 10_000L
                ),
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 1, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(2)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        chestplate = leatherArmor(
                            XMaterial.LEATHER_CHESTPLATE,
                            PIGMAN_COLOR,
                            mapOf(PROT to 1, FIRE_PROT to 1, UNBREAKING to 1)
                        ),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 1, UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 1, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(2)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 3, UNBREAKING to 1)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 2, UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 1, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(3)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 3, PROT to 1, UNBREAKING to 1)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 2, FIRE_PROT to 5, UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 2, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(3)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, PIGMAN_COLOR, mapOf(PROT to 1, UNBREAKING to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, PIGMAN_COLOR, mapOf(PROT to 2, UNBREAKING to 1)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 3, PROT to 1, UNBREAKING to 1)),
                        helmet = item(XMaterial.GOLDEN_HELMET, 1, mapOf(PROT to 1, FIRE_PROT to 5, UNBREAKING to 1))
                    ),
                    inv(
                        item(XMaterial.GOLDEN_SWORD, 1, mapOf(SHARPNESS to 2, UNBREAKING to 10)),
                        spawnEgg(ENTITY_PIG_ZOMBIE, 1),
                        harmingSplash(4)
                    )
                )
            )
        )
    }

    // ==============================
    // WOLFTAMER KIT
    // ==============================
    private fun buildWolftamerKit(): HungerGamesKit
    {
        val icon = item(XMaterial.BONE)

        return HungerGamesKit(
            id = "wolftamer",
            displayName = "Wolftamer",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR),
                        chestplate = null,
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_AXE),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.ROTTEN_FLESH, 14)
                    ),
                    price = 40_000L
                ),
                level(
                    2,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WOLFTAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_AXE),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.ROTTEN_FLESH, 16)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WOLFTAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_AXE),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.ROTTEN_FLESH, 18)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 3)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WOLFTAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        spawnEgg(ENTITY_WOLF, 4),
                        item(XMaterial.ROTTEN_FLESH, 18)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 4)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WOLFTAMER_COLOR),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        spawnEgg(ENTITY_WOLF, 4),
                        item(XMaterial.ROTTEN_FLESH, 20)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.DIAMOND_BOOTS, 1, mapOf(PROT to 4)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, WOLFTAMER_COLOR, mapOf(PROT to 1)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, WOLFTAMER_COLOR, mapOf(PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, WOLFTAMER_COLOR, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        spawnEgg(ENTITY_WOLF, 5),
                        item(XMaterial.ROTTEN_FLESH, 24)
                    )
                )
            )
        )
    }

    // ==============================
    // BLAZE KIT
    // ==============================
    private fun buildBlazeKit(): HungerGamesKit
    {
        val icon = item(XMaterial.BLAZE_ROD)

        return HungerGamesKit(
            id = "blaze",
            displayName = "Blaze",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 2)
                    ),
                    price = 20_000L
                ),
                level(
                    2,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 3, PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 2)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 4, PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 3)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = item(XMaterial.GOLDEN_CHESTPLATE, 1, mapOf(FIRE_PROT to 4, PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 3)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(FIRE_PROT to 10)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 3)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, BLAZE_COLOR, mapOf(FIRE_PROT to 2)),
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(FIRE_PROT to 10, PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, BLAZE_COLOR, mapOf(FIRE_PROT to 2))
                    ),
                    inv(
                        item(XMaterial.IRON_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.STONE_AXE, 1, mapOf(FIRE_ASPECT to 1)),
                        spawnEgg(ENTITY_BLAZE, 4)
                    )
                )
            )
        )
    }

    // ==============================
    // CREEPER KIT
    // ==============================
    private fun buildCreeperKit(): HungerGamesKit
    {
        val icon = item(XMaterial.TNT)

        return HungerGamesKit(
            id = "creeper",
            displayName = "Creeper",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = null,
                        leggings = null,
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(BLAST_PROT to 4)),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        item(XMaterial.TNT, 6),
                        spawnEgg(ENTITY_CREEPER, 4)
                    ),
                    price = 30_000L
                ),
                level(
                    2,
                    armor(
                        boots = null,
                        leggings = null,
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(BLAST_PROT to 4, PROT to 1)),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        item(XMaterial.TNT, 7),
                        spawnEgg(ENTITY_CREEPER, 4)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, CREEPER_COLOR),
                        leggings = null,
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(BLAST_PROT to 4, PROT to 1)),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.WOODEN_SWORD),
                        item(XMaterial.TNT, 8),
                        spawnEgg(ENTITY_CREEPER, 4)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, CREEPER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, CREEPER_COLOR),
                        chestplate = item(XMaterial.IRON_CHESTPLATE, 1, mapOf(BLAST_PROT to 4, PROT to 1)),
                        helmet = null
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD, 1, mapOf(UNBREAKING to 1)),
                        item(XMaterial.TNT, 9),
                        spawnEgg(ENTITY_CREEPER, 4)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, CREEPER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, CREEPER_COLOR),
                        chestplate = item(XMaterial.DIAMOND_CHESTPLATE, 1, mapOf(BLAST_PROT to 10)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, CREEPER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        item(XMaterial.TNT, 10),
                        spawnEgg(ENTITY_CREEPER, 5)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = leatherArmor(XMaterial.LEATHER_BOOTS, CREEPER_COLOR),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, CREEPER_COLOR),
                        chestplate = item(XMaterial.DIAMOND_CHESTPLATE, 1, mapOf(BLAST_PROT to 10, PROT to 1)),
                        helmet = leatherArmor(XMaterial.LEATHER_HELMET, CREEPER_COLOR)
                    ),
                    inv(
                        item(XMaterial.STONE_SWORD),
                        item(XMaterial.TNT, 12),
                        spawnEgg(ENTITY_CREEPER, 6)
                    )
                )
            )
        )
    }

    // ==============================
    // SNOWMAN KIT
    // ==============================
    private fun buildSnowmanKit(): HungerGamesKit
    {
        val icon = item(XMaterial.SNOWBALL)

        return HungerGamesKit(
            id = "snowman",
            displayName = "Snowman",
            icon = icon,
            levels = mutableMapOf(
                level(
                    1,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET)
                    ),
                    inv(
                        item(XMaterial.STONE_AXE),
                        item(XMaterial.SNOWBALL, 16),
                        spawnEgg(ENTITY_WOLF, 2),
                        item(XMaterial.CARROT, 6)
                    ),
                    price = 385_000L
                ),
                level(
                    2,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE),
                        item(XMaterial.SNOWBALL, 16),
                        spawnEgg(ENTITY_WOLF, 2),
                        item(XMaterial.CARROT, 6)
                    )
                ),
                level(
                    3,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROT to 1))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE),
                        item(XMaterial.SNOWBALL, 32),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.CARROT, 6)
                    )
                ),
                level(
                    4,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 1)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROT to 2))
                    ),
                    inv(
                        item(XMaterial.IRON_AXE),
                        item(XMaterial.SNOWBALL, 32),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.CARROT, 6)
                    )
                ),
                level(
                    5,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 2)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE),
                        item(XMaterial.SNOWBALL, 48),
                        spawnEgg(ENTITY_WOLF, 3),
                        item(XMaterial.CARROT, 10)
                    )
                ),
                level(
                    6,
                    armor(
                        boots = item(XMaterial.IRON_BOOTS, 1, mapOf(PROJ_PROT to 3)),
                        leggings = leatherArmor(XMaterial.LEATHER_LEGGINGS, SNOWMAN_COLOR),
                        chestplate = leatherArmor(XMaterial.LEATHER_CHESTPLATE, SNOWMAN_COLOR),
                        helmet = item(XMaterial.IRON_HELMET, 1, mapOf(PROT to 3))
                    ),
                    inv(
                        item(XMaterial.DIAMOND_AXE),
                        item(XMaterial.SNOWBALL, 64),
                        spawnEgg(ENTITY_WOLF, 4),
                        item(XMaterial.CARROT, 10)
                    )
                )
            )
        )
    }
}
