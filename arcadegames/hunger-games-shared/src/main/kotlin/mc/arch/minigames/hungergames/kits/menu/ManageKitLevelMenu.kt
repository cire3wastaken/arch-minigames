package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ManageKitLevelMenu(
    private val kitId: String,
    private val level: Int
) : Menu("Kit '$kitId' - Level $level")
{
    init
    {
        shouldLoadInSync()
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>) = 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        // Save from current inventory
        buttons[11] = ItemBuilder
            .of(XMaterial.LIME_DYE)
            .name("${CC.B_GREEN}Save Inventory")
            .addToLore(
                "${CC.GRAY}Saves your CURRENT armor",
                "${CC.GRAY}and inventory to this",
                "${CC.GRAY}kit level.",
                "",
                "${CC.YELLOW}Click to save!"
            )
            .toButton { _, _ ->
                HungerGamesKitDataSync.editAndSave {
                    val kitLevel = kits[kitId]!!.levels[level] ?: return@editAndSave

                    kitLevel.armor = player.inventory.armorContents.map { it?.clone() }.toTypedArray()
                    kitLevel.inventory = (0 until 36).map { slot ->
                        player.inventory.getItem(slot)?.clone()
                    }.toTypedArray()
                }

                player.sendMessage("${CC.GREEN}Kit level $level inventory saved!")
            }

        // Preview
        buttons[13] = ItemBuilder
            .of(XMaterial.ENDER_EYE)
            .name("${CC.B_AQUA}Preview Kit")
            .addToLore(
                "${CC.GRAY}Gives you the items from",
                "${CC.GRAY}this kit level. Your current",
                "${CC.GRAY}inventory will be cleared.",
                "",
                "${CC.YELLOW}Click to preview!"
            )
            .toButton { _, _ ->
                player.closeInventory()
                player.inventory.clear()
                player.inventory.armorContents = arrayOfNulls(4)

                val kit = HungerGamesKitDataSync.cached().kits[kitId]
                kit?.applyTo(player, level)

                player.sendMessage("${CC.GREEN}Previewing kit '$kitId' level $level. Modify and use /managehgkits to save.")
            }

        // Info
        buttons[15] = ItemBuilder
            .of(XMaterial.PAPER)
            .name("${CC.B_YELLOW}Info")
            .addToLore(
                "${CC.GRAY}Kit: ${CC.WHITE}$kitId",
                "${CC.GRAY}Level: ${CC.WHITE}$level",
                "",
                "${CC.GRAY}To set this level's items:",
                "${CC.WHITE}1. ${CC.GRAY}Put items in your inventory",
                "${CC.WHITE}2. ${CC.GRAY}Click 'Save Inventory'",
            )
            .toButton()

        return buttons
    }

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Schedulers
                .sync()
                .run {
                    ManageKitMenu(kitId).openMenu(player)
                }
        }
    }
}
