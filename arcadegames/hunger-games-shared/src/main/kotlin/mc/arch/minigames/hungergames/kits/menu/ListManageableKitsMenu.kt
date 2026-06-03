package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.pagination.PaginatedMenu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author ArchMC
 */
class ListManageableKitsMenu : PaginatedMenu()
{
    init
    {
        shouldLoadInSync()
    }

    override fun getPrePaginatedTitle(player: Player) = "HG Kits"

    override fun getAllPagesButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        HungerGamesKitDataSync.cached().kits.values.forEach { kit ->
            buttons[buttons.size] = runCatching {
                ItemBuilder
                    .copyOf(kit.icon)
            }.getOrElse {
                ItemBuilder.of(XMaterial.BARRIER)
            }
                .name("${CC.GREEN}${kit.displayName}")
                .addToLore(
                    "${CC.GRAY}ID: ${kit.id}",
                    "${CC.GRAY}Levels: ${kit.levels.size}",
                    "",
                    "${CC.YELLOW}Click to manage!"
                )
                .toButton { _, _ ->
                    ManageKitMenu(kit.id).openMenu(player)
                }
        }

        return buttons
    }
}
