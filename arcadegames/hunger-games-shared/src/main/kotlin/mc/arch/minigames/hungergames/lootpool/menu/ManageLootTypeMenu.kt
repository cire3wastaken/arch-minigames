package mc.arch.minigames.hungergames.lootpool.menu

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.configurable.editDouble
import mc.arch.minigames.hungergames.lootpool.HGLootDataSync
import mc.arch.minigames.hungergames.lootpool.HGLootType
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ManageLootTypeMenu(
    private val type: HGLootType
) : Menu("HG Loot - ${type.name}")
{
    init
    {
        shouldLoadInSync()
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>) = 27
    override fun getButtons(player: Player) = mapOf(
        12 to editDouble(
            HGLootDataSync,
            title = "Fill Percentage",
            material = XMaterial.DIAMOND,
            {
                types[type]!!.fillPercentage
            },
            {
                types[type]!!.fillPercentage = it
            }
        ),
        14 to ItemBuilder
            .of(XMaterial.CHEST)
            .name("${CC.B_YELLOW}Edit Candidates")
            .addToLore(
                "",
                "${CC.YELLOW}Click to open!"
            )
            .toButton { _, _ ->
                ManageLootCandidatesMenu(type).openMenu(player)
            }
    )

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Schedulers
                .sync()
                .run {
                    ViewLootTypesMenu().openMenu(player)
                }
        }
    }
}
