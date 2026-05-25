package mc.arch.minigame.pof.loadout

import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CosmeticLoadout(
    val player: UUID,
    val selections: EnumMap<CosmeticType, String> = EnumMap(CosmeticType::class.java)
)
{
    fun selection(type: CosmeticType): String? = selections[type]
    fun equip(type: CosmeticType, cosmeticId: String)
    {
        selections[type] = cosmeticId
    }
    fun unequip(type: CosmeticType)
    {
        selections.remove(type)
    }
}

object CosmeticLoadoutCache
{
    private val loadouts = ConcurrentHashMap<UUID, CosmeticLoadout>()

    fun get(player: UUID): CosmeticLoadout =
        loadouts.getOrPut(player) { CosmeticLoadout(player) }

    fun invalidate(player: UUID)
    {
        loadouts.remove(player)
    }
}
