package mc.arch.minigames.arcade.lobby.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.arcade.lobby.extension.ArcadeGameExtensionRegistry
import mc.arch.minigames.arcade.lobby.extension.ArcadeManageButton
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

class ArcadeManageMenu : Menu("Arcade Customization")
{
    override fun size(buttons: Map<Int, Button>) = 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val entries = manageableEntries(player)
        val slots = centeredSlotLayout(entries.size)

        return entries
            .withIndex()
            .associate { (index, entry) ->
                slots.getOrElse(index) { 11 + index } to manageButton(player, entry)
            }
    }

    private data class ManageEntry(val cardName: String, val icon: XMaterial, val manage: ArcadeManageButton)

    private fun manageableEntries(player: Player): List<ManageEntry> = ArcadeCatalog.cards
        .mapNotNull { card ->
            val manage = ArcadeGameExtensionRegistry
                .forId(card.internalId)
                ?.manageButton(player)
                ?: return@mapNotNull null
            ManageEntry(card.cardName, card.icon, manage)
        }
        .distinctBy { it.cardName to it.manage.displayName }

    private fun manageButton(player: Player, entry: ManageEntry): Button = ItemBuilder
        .of(entry.icon)
        .name("${CC.BD_PURPLE}${entry.cardName}")
        .apply { entry.manage.lore.forEach { addToLore("${CC.GRAY}$it") } }
        .addToLore(
            "",
            "${CC.LIGHT_PURPLE}Click to open ${entry.manage.displayName}!"
        )
        .toButton { _, _ ->
            Button.playNeutral(player)
            entry.manage.onClick(player)
        }

    private fun centeredSlotLayout(count: Int): List<Int> = when (count)
    {
        0 -> emptyList()
        1 -> listOf(13)
        2 -> listOf(12, 14)
        3 -> listOf(11, 13, 15)
        4 -> listOf(11, 12, 14, 15)
        else -> listOf(10, 11, 12, 13, 14, 15, 16)
    }
}
