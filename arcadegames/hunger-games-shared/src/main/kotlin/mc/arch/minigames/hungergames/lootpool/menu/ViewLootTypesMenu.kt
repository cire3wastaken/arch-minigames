package mc.arch.minigames.hungergames.lootpool.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.lootpool.HGLootDataSync
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ViewLootTypesMenu : Menu("HG Loot - Select Type")
{
    init
    {
        shouldLoadInSync()
    }

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()
        HGLootDataSync.cached()
            .types
            .keys
            .forEach { type ->
                buttons[buttons.size] = ItemBuilder
                    .of(XMaterial.OAK_SIGN)
                    .name("${CC.B_YELLOW}Type ${type.name}")
                    .addToLore(
                        "",
                        "${CC.YELLOW}Click to manage!"
                    )
                    .toButton { _, _ ->
                        ManageLootTypeMenu(type).openMenu(player)
                    }
            }
        return buttons
    }
}
