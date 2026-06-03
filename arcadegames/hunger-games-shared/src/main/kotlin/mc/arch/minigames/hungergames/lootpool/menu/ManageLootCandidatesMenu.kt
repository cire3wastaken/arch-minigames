package mc.arch.minigames.hungergames.lootpool.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.lootpool.HGLootCandidate
import mc.arch.minigames.hungergames.lootpool.HGLootDataSync
import mc.arch.minigames.hungergames.lootpool.HGLootType
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.menus.SelectItemStackMenu
import net.evilblock.cubed.menu.pagination.PaginatedMenu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ManageLootCandidatesMenu(
    private val type: HGLootType
) : PaginatedMenu()
{
    init
    {
        shouldLoadInSync()
    }

    override fun getPrePaginatedTitle(player: Player) = "HG Loot - ${type.name} Candidates"
    override fun getGlobalButtons(player: Player) = mapOf(
        4 to ItemBuilder
            .of(XMaterial.EGG)
            .name("${CC.B_YELLOW}Add a new Item Candidate")
            .addToLore(
                "",
                "${CC.YELLOW}Click to add!"
            )
            .toButton { _, _ ->
                SelectItemStackMenu {
                    HGLootDataSync.editAndSave {
                        types[type]!!.candidates += HGLootCandidate(item = it)
                    }

                    Schedulers
                        .async()
                        .runLater({
                            openMenu(player)
                        }, 2L)
                }.openMenu(player)
            }
    )

    override fun getAllPagesButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        HGLootDataSync
            .cached()
            .types[type]!!
            .candidates
            .forEachIndexed { index, it ->
                buttons[buttons.size] = runCatching {
                    ItemBuilder
                        .copyOf(it.toItem())
                        .addToLore("")
                }.getOrElse {
                    ItemBuilder
                        .of(XMaterial.BARRIER)
                        .addToLore("${CC.RED}*Invalid Item*")
                }.addToLore(
                    "${CC.YELLOW}Amount: ${CC.WHITE}${it.amountRangeMin} <-> ${it.amountRangeMax}",
                    "${CC.YELLOW}Weight: ${CC.WHITE}${
                        "%.2f".format(it.weight.toFloat())
                    }%",
                    "",
                    "${CC.GREEN}Click to edit!",
                    "${CC.RED}Shift-Click to remove!"
                )
                    .toButton { _, click ->
                        if (click?.isShiftClick == true)
                        {
                            HGLootDataSync.editAndSave {
                                types[type]!!.candidates.removeAt(index)
                            }

                            Schedulers
                                .async()
                                .runLater({
                                    openMenu(player)
                                }, 2L)
                        } else
                        {
                            ManageLootCandidateMenu(type, index).openMenu(player)
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
                    ManageLootTypeMenu(type).openMenu(player)
                }
        }
    }
}
