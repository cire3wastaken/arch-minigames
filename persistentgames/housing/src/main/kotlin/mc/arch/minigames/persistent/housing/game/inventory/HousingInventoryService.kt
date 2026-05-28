package mc.arch.minigames.persistent.housing.game.inventory

import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.item.HousingItemService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object HousingInventoryService
{
    fun isEnabled(house: PlayerHouse): Boolean = house.saveInventoriesEnabled != false

    fun snapshotInventory(player: Player): String?
    {
        val contents = player.inventory.contents
        val filtered = arrayOfNulls<ItemStack>(contents.size)
        var hasAnything = false

        for (i in contents.indices)
        {
            val item = contents[i] ?: continue
            if (item.isSimilar(HousingItemService.realmItem)) continue

            filtered[i] = item.clone()
            hasAnything = true
        }

        if (!hasAnything) return null

        return runCatching {
            val out = ByteArrayOutputStream()
            BukkitObjectOutputStream(out).use { stream ->
                stream.writeInt(filtered.size)
                for (item in filtered)
                {
                    stream.writeObject(item)
                }
            }
            String(Base64Coder.encode(out.toByteArray()))
        }.getOrNull()
    }

    fun restoreInventory(player: Player, base64: String): Boolean
    {
        if (base64.isEmpty()) return false

        return runCatching {
            val input = ByteArrayInputStream(Base64Coder.decode(base64))
            BukkitObjectInputStream(input).use { stream ->
                val size = stream.readInt()
                val contents = player.inventory.contents

                for (i in 0 until size)
                {
                    val item = stream.readObject() as? ItemStack
                    if (i >= contents.size) continue

                    if (item != null && item.isSimilar(HousingItemService.realmItem))
                    {
                        continue
                    }

                    contents[i] = item
                }

                player.inventory.contents = contents
                player.updateInventory()
            }
            true
        }.getOrElse { false }
    }

    fun clearInventory(player: Player)
    {
        val contents = player.inventory.contents

        for (i in contents.indices)
        {
            val item = contents[i] ?: continue
            if (item.isSimilar(HousingItemService.realmItem)) continue
            contents[i] = null
        }

        player.inventory.contents = contents
        player.inventory.helmet = null
        player.inventory.chestplate = null
        player.inventory.leggings = null
        player.inventory.boots = null
        player.updateInventory()
    }

    fun save(player: Player, house: PlayerHouse)
    {
        if (!isEnabled(house)) return

        val encoded = snapshotInventory(player)
        val map = house.savedInventories
            ?: mutableMapOf<String, String>().also { house.savedInventories = it }

        val key = player.uniqueId.toString()
        if (encoded == null)
        {
            if (map.remove(key) != null)
            {
                house.save()
            }
            return
        }

        map[key] = encoded
        house.save()
    }

    fun load(player: Player, house: PlayerHouse)
    {
        if (!isEnabled(house)) return

        val map = house.savedInventories ?: return
        val data = map[player.uniqueId.toString()] ?: return

        if (data.isEmpty()) return

        restoreInventory(player, data)
    }
}
