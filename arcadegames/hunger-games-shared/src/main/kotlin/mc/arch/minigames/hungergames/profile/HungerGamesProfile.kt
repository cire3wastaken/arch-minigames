package mc.arch.minigames.hungergames.profile

import gg.scala.store.controller.DataStoreObjectControllerCache
import gg.scala.store.storage.storable.IDataStoreObject
import gg.scala.store.storage.type.DataStoreStorageType
import org.bukkit.inventory.ItemStack
import java.util.*

data class HungerGamesKitStats(
    var damageDealt: Double = 0.0,
    var damageTaken: Double = 0.0,
    var kills: Long = 0L,
    var deaths: Long = 0L,
    var gamesPlayed: Long = 0L
)

data class HungerGamesProfile(
    override val identifier: UUID,
    var selectedKit: String? = null,
    var selectedKitLevel: Int = 1,
    val purchasedKits: MutableMap<String, MutableSet<Int>> = mutableMapOf(),
    var totalKills: Long = 0L,
    var totalDeaths: Long = 0L,
    val kitStats: MutableMap<String, HungerGamesKitStats> = mutableMapOf(),
    val customLoadouts: MutableMap<String, Array<ItemStack?>> = mutableMapOf(),
    val kitPrestiges: MutableMap<String, Int> = mutableMapOf()
) : IDataStoreObject
{
    companion object
    {
        /**
         * Kits that are free by default (no kill requirement).
         */
        val DEFAULT_KITS = setOf(
            "baker", "knight", "archer", "meatmaster", "scout", "armorer"
        )

        /**
         * Kill requirements for kits. Kits not listed here and not in DEFAULT_KITS
         * have no kill requirement (level 1 is still free).
         */
        val KIT_KILL_REQUIREMENTS: Map<String, Long> = mapOf(
            "shadow" to 500L,
            "pigman" to 500L,
            "creeper" to 500L,
            "wolftamer" to 1000L,
            "blaze" to 1000L,
            "slime" to 1000L,
            "astronaut" to 2500L,
            "horsetamer" to 2500L,
            "warlock" to 2500L,
            "snowman" to 5000L
        )

        /**
         * Get the kill requirement for a kit, or 0 if none.
         */
        fun killRequirement(kitId: String): Long = KIT_KILL_REQUIREMENTS[kitId] ?: 0L

        /**
         * Kit kills required to prestige.
         */
        const val PRESTIGE_KILL_REQUIREMENT = 2500L

        /**
         * Coins required to prestige.
         */
        const val PRESTIGE_COIN_REQUIREMENT = 1_000_000L
    }

    /**
     * Get per-kit statistics, creating a fresh entry if none exists.
     */
    fun getStatsFor(kitId: String): HungerGamesKitStats =
        kitStats.getOrPut(kitId) { HungerGamesKitStats() }

    /**
     * Get the prestige level for a kit (0 if never prestiged).
     */
    fun getPrestige(kitId: String): Int = kitPrestiges[kitId] ?: 0

    /**
     * Check if the player can prestige a kit.
     * Requires 2,500 kit kills and has not already prestiged.
     */
    fun canPrestige(kitId: String): Boolean
    {
        if (getPrestige(kitId) >= 1) return false
        return getStatsFor(kitId).kills >= PRESTIGE_KILL_REQUIREMENT
    }

    /**
     * Check if the player meets the kill requirement to use/purchase a kit.
     */
    fun meetsKillRequirement(kitId: String): Boolean
    {
        val required = killRequirement(kitId)
        return required <= 0L || totalKills >= required
    }

    /**
     * Check if the player owns a specific kit level.
     * Level 1 of every kit is free by default.
     */
    fun hasKit(kitId: String, level: Int): Boolean
    {
        if (level <= 1) return true
        return purchasedKits[kitId]?.contains(level) == true
    }

    /**
     * Check if the player owns any level of a kit (at minimum level 1, which is always free).
     */
    fun ownsAnyLevel(kitId: String): Boolean = true

    /**
     * Unlock a specific kit level for this player.
     */
    fun unlockKit(kitId: String, level: Int)
    {
        purchasedKits.getOrPut(kitId) { mutableSetOf() }.add(level)
    }

    /**
     * Get the highest level the player owns for a kit.
     */
    fun highestOwnedLevel(kitId: String, maxLevel: Int): Int
    {
        val owned = purchasedKits[kitId] ?: return 1
        return (1..maxLevel).lastOrNull { it == 1 || it in owned } ?: 1
    }

    fun save() = DataStoreObjectControllerCache
        .findNotNull<HungerGamesProfile>()
        .save(this, DataStoreStorageType.MONGO)
}

