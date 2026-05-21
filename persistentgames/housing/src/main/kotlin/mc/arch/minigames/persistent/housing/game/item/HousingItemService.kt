package mc.arch.minigames.persistent.housing.game.item

import com.cryptomorin.xseries.XMaterial
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.persistent.housing.game.menu.house.MainHouseMenu
import mc.arch.minigames.persistent.housing.game.menu.player.PlayerInteractViewMenu
import mc.arch.minigames.persistent.housing.game.resources.getPlayerHouseFromInstance
import me.lucko.helper.Events
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Class created on 12/28/2025

 * @author Max C.
 * @project arch-minigames
 * @website https://solo.to/redis
 */
@Service
object HousingItemService
{
    val realmItem = ItemBuilder.of(XMaterial.NETHER_STAR)
        .name("${CC.PINK}Realm Info ${CC.GRAY}(Right Click)")
        .build()

    @Configure
    fun configure()
    {
        Events.subscribe(InventoryDragEvent::class.java)
            .filter { it.cursor?.isSimilar(realmItem) == true }
            .handler { event ->
                event.isCancelled = true
            }

        Events.subscribe(InventoryMoveItemEvent::class.java)
            .filter { it.item.isSimilar(realmItem) }
            .handler { event ->
                event.isCancelled = true
            }

        Events.subscribe(PlayerDropItemEvent::class.java)
            .filter {
                it.itemDrop.itemStack.isSimilar(realmItem)
            }
            .handler { event ->
                event.isCancelled = true
            }

        Events.subscribe(PlayerInteractEvent::class.java)
            .filter { it.action == Action.RIGHT_CLICK_AIR || it.action == Action.RIGHT_CLICK_BLOCK }
            .filter { it.item != null && it.item.isSimilar(realmItem) }
            .handler { event ->
                val house = event.player.getPlayerHouseFromInstance()
                    ?: return@handler

                MainHouseMenu(house, house.playerIsOrAboveAdministrator(event.player.uniqueId))
                    .openMenu(event.player)
            }

        Events.subscribe(PlayerInteractAtEntityEvent::class.java)
            .filter { it.rightClicked is Player }
            .handler { event ->
                val clicked = event.rightClicked as Player
                val clicker = event.player

                val house = clicked.getPlayerHouseFromInstance()
                    ?: return@handler

                PlayerInteractViewMenu(house, clicked).openMenu(clicker)
            }
    }
}