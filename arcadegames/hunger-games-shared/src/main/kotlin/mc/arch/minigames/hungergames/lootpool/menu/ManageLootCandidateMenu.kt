package mc.arch.minigames.hungergames.lootpool.menu

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.configurable.editDouble
import gg.scala.commons.configurable.editInt
import gg.scala.commons.configurable.editItemStack
import mc.arch.minigames.hungergames.lootpool.HGLootCandidate
import mc.arch.minigames.hungergames.lootpool.HGLootContainer
import mc.arch.minigames.hungergames.lootpool.HGLootDataSync
import mc.arch.minigames.hungergames.lootpool.HGLootType
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.menu.buttons.RemoveButton
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ManageLootCandidateMenu(
    private val type: HGLootType,
    private val itemIndex: Int
) : Menu("HG Loot - Edit Candidate")
{
    init
    {
        shouldLoadInSync()
    }

    fun HGLootContainer.candidateOf() = types[type]!!.candidates[itemIndex]
    override fun getButtons(player: Player) = mapOf(
        0 to editItemStack(
            HGLootDataSync,
            title = "Item",
            {
                candidateOf().item ?: ItemBuilder.of(XMaterial.BARRIER).build()
            },
            {
                candidateOf().item = it
                candidateOf().amountRangeMin = it.amount
                candidateOf().amountRangeMax = it.amount
            }
        ),
        1 to editInt(
            HGLootDataSync,
            title = "Range Min",
            material = XMaterial.NAME_TAG,
            {
                candidateOf().amountRangeMin
            },
            {
                candidateOf().amountRangeMin = it
            },
            range = 1..64
        ),
        2 to editInt(
            HGLootDataSync,
            title = "Range Max",
            material = XMaterial.NAME_TAG,
            {
                candidateOf().amountRangeMax
            },
            {
                candidateOf().amountRangeMax = it
            },
            range = 1..64
        ),
        3 to editDouble(
            HGLootDataSync,
            title = "Weight",
            material = XMaterial.ANVIL,
            {
                candidateOf().weight
            },
            {
                candidateOf().weight = it
            },
            range = 0.01..100.00
        ),
        8 to RemoveButton {
            player.closeInventory()
            player.sendMessage("${CC.B_GREEN}${Constants.CHECK_SYMBOL}")

            HGLootDataSync.editAndSave {
                types[type]!!.candidates.removeAt(itemIndex)
            }

            Schedulers
                .sync()
                .runLater({
                    ManageLootCandidatesMenu(type).openMenu(player)
                }, 2L)
        }
    )

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Schedulers
                .sync()
                .run {
                    ManageLootCandidatesMenu(type).openMenu(player)
                }
        }
    }
}
