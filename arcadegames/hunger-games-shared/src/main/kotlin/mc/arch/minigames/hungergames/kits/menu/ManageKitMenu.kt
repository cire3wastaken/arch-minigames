package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import mc.arch.minigames.hungergames.kits.HungerGamesKitLevel
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ManageKitMenu(
    private val kitId: String
) : Menu("Manage Kit: $kitId")
{
    init
    {
        shouldLoadInSync()
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>) = 36

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()
        val kit = HungerGamesKitDataSync.cached().kits[kitId] ?: return buttons

        // Add level button
        buttons[4] = ItemBuilder
            .of(XMaterial.EMERALD)
            .name("${CC.B_GREEN}Add New Level")
            .addToLore(
                "${CC.GRAY}Current levels: ${kit.levels.size}",
                "",
                "${CC.YELLOW}Click to add level ${kit.maxLevel() + 1}!"
            )
            .toButton { _, _ ->
                val nextLevel = kit.maxLevel() + 1
                HungerGamesKitDataSync.editAndSave {
                    kits[kitId]!!.levels[nextLevel] = HungerGamesKitLevel(level = nextLevel)
                }
                player.sendMessage("${CC.GREEN}Added level $nextLevel to kit '$kitId'!")

                Schedulers
                    .sync()
                    .runLater({
                        ManageKitMenu(kitId).openMenu(player)
                    }, 2L)
            }

        // Set icon button
        buttons[0] = ItemBuilder
            .of(XMaterial.PAINTING)
            .name("${CC.B_YELLOW}Set Icon")
            .addToLore(
                "${CC.GRAY}Sets the kit icon to the",
                "${CC.GRAY}item in your main hand.",
                "",
                "${CC.YELLOW}Click to set!"
            )
            .toButton { _, _ ->
                val item = player.itemInHand
                if (item == null || item.type == XMaterial.AIR.parseMaterial())
                {
                    player.sendMessage("${CC.RED}Hold an item in your hand!")
                    return@toButton
                }

                HungerGamesKitDataSync.editAndSave {
                    kits[kitId]!!.icon = item.clone()
                }
                player.sendMessage("${CC.GREEN}Icon updated!")
            }

        // Delete kit button
        buttons[8] = ItemBuilder
            .of(XMaterial.BARRIER)
            .name("${CC.B_RED}Delete Kit")
            .addToLore(
                "${CC.RED}This cannot be undone!",
                "",
                "${CC.RED}Shift-Click to delete!"
            )
            .toButton { _, click ->
                if (click?.isShiftClick == true)
                {
                    HungerGamesKitDataSync.editAndSave {
                        kits.remove(kitId)
                    }
                    player.closeInventory()
                    player.sendMessage("${CC.RED}Kit '$kitId' deleted!")
                }
            }

        // Level buttons
        val levelSlots = (18..26)
        kit.levels.entries.sortedBy { it.key }.forEachIndexed { index, (level, _) ->
            if (index < levelSlots.count())
            {
                buttons[levelSlots.elementAt(index)] = ItemBuilder
                    .of(XMaterial.BOOK)
                    .name("${CC.GREEN}Level $level")
                    .addToLore(
                        "${CC.GRAY}Click to edit armor",
                        "${CC.GRAY}and inventory contents.",
                        "",
                        "${CC.YELLOW}Click to manage!",
                        "${CC.RED}Shift-Click to remove!"
                    )
                    .amount(level.coerceIn(1, 64))
                    .toButton { _, click ->
                        if (click?.isShiftClick == true)
                        {
                            HungerGamesKitDataSync.editAndSave {
                                kits[kitId]!!.levels.remove(level)
                            }
                            player.sendMessage("${CC.RED}Level $level removed!")

                            Schedulers
                                .sync()
                                .runLater({
                                    ManageKitMenu(kitId).openMenu(player)
                                }, 2L)
                        } else
                        {
                            ManageKitLevelMenu(kitId, level).openMenu(player)
                        }
                    }
            }
        }

        return buttons
    }

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Schedulers
                .sync()
                .run {
                    ListManageableKitsMenu().openMenu(player)
                }
        }
    }
}
