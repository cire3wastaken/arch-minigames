package mc.arch.minigames.persistent.housing.game.worldedit

import com.cryptomorin.xseries.XMaterial
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.resources.getPlayerHouseFromInstance
import me.lucko.helper.Events
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID

@Service
object WorldEditService
{
    const val MAX_AXIS = 100
    const val WAND_PERMISSION_NODE = "worldedit.use"

    val wandItem: ItemStack = ItemBuilder.of(XMaterial.WOODEN_AXE)
        .name("${CC.GOLD}Realm WorldEdit Wand")
        .addToLore(
            "${CC.GRAY}Left-click a block: ${CC.YELLOW}set pos1",
            "${CC.GRAY}Right-click a block: ${CC.YELLOW}set pos2",
            "",
            "${CC.GRAY}Use ${CC.AQUA}//set ${CC.GRAY}, ${CC.AQUA}//cut${CC.GRAY},",
            "${CC.AQUA}//copy${CC.GRAY}, ${CC.AQUA}//paste${CC.GRAY}, ${CC.AQUA}//sphere${CC.GRAY}.",
            "",
            "${CC.RED}Max ${MAX_AXIS}x${MAX_AXIS}x${MAX_AXIS} per operation."
        )
        .build()

    private val sessions = mutableMapOf<UUID, WorldEditSession>()

    fun sessionOf(player: Player): WorldEditSession =
        sessions.getOrPut(player.uniqueId) { WorldEditSession() }

    fun clear(uuid: UUID)
    {
        sessions.remove(uuid)
    }

    fun canUseWorldEdit(player: Player, house: PlayerHouse): Boolean
    {
        if (house.playerIsOrAboveAdministrator(player.uniqueId)) return true
        if (house.hasPermission(player.uniqueId, WAND_PERMISSION_NODE)) return true
        if (player.hasPermission(WAND_PERMISSION_NODE)) return true
        return false
    }

    private fun isWand(item: ItemStack?): Boolean
    {
        if (item == null || item.type != Material.WOOD_AXE) return false
        return item.isSimilar(wandItem)
    }

    @Configure
    fun configure()
    {
        Events.subscribe(PlayerInteractEvent::class.java)
            .filter { isWand(it.item) }
            .handler { event ->
                val player = event.player
                val house = player.getPlayerHouseFromInstance() ?: return@handler

                if (!canUseWorldEdit(player, house))
                {
                    return@handler
                }

                when (event.action)
                {
                    Action.LEFT_CLICK_BLOCK ->
                    {
                        val block = event.clickedBlock ?: return@handler
                        event.isCancelled = true
                        val session = sessionOf(player)
                        session.setPos1(block.location.toVector(), block.world.name)
                        player.sendMessage(
                            "${CC.GREEN}pos1 ${CC.GRAY}set to ${CC.WHITE}${block.x}, ${block.y}, ${block.z}"
                        )
                    }
                    Action.RIGHT_CLICK_BLOCK ->
                    {
                        val block = event.clickedBlock ?: return@handler
                        event.isCancelled = true
                        val session = sessionOf(player)
                        session.setPos2(block.location.toVector(), block.world.name)
                        player.sendMessage(
                            "${CC.GREEN}pos2 ${CC.GRAY}set to ${CC.WHITE}${block.x}, ${block.y}, ${block.z}"
                        )
                    }
                    else -> {}
                }
            }

        // Cancelling PlayerInteractEvent on LEFT_CLICK_BLOCK is unreliable in
        // creative mode — the block can still break before our handler runs.
        // Belt-and-braces: also veto BlockBreakEvent when the wand is held.
        Events.subscribe(BlockBreakEvent::class.java)
            .filter { isWand(it.player.itemInHand) }
            .handler { it.isCancelled = true }

        Events.subscribe(PlayerQuitEvent::class.java)
            .handler { clear(it.player.uniqueId) }
    }
}
