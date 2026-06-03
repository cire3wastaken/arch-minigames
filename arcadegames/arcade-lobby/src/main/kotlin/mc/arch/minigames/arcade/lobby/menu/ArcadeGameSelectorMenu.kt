package mc.arch.minigames.arcade.lobby.menu

import gg.tropic.practice.metadata.SystemMetadataService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

class ArcadeGameSelectorMenu : Menu("Arcade Games")
{
    override fun size(buttons: Map<Int, Button>) = 45

    override fun getButtons(player: Player): Map<Int, Button> = ArcadeCatalog.cards
        .associate { card -> card.slot to gameButton(player, card) }

    private fun gameButton(player: Player, card: ArcadeCard): Button
    {
        val modeQueueIds = card.modes.mapTo(HashSet()) { it.queueId }
        val playing = SystemMetadataService
            .allGames()
            .filter { it.queueId in modeQueueIds }
            .sumOf { it.players.size }

        return ItemBuilder
            .of(card.icon)
            .name("${CC.BD_PURPLE}${card.cardName}")
            .apply { card.lore.forEach { addToLore("${CC.GRAY}$it") } }
            .addToLore("")
            .addToLore("${CC.LIGHT_PURPLE}Click to play!")
            .addToLore("${CC.GRAY}$playing currently playing!")
            .toButton { _, _ ->
                Button.playNeutral(player)

                val singleMode = card.modes.singleOrNull()
                if (singleMode != null)
                {
                    ArcadeGameMenu.queue(player, card.cardName, singleMode)
                } else
                {
                    ArcadeGameMenu(card).openMenu(player)
                }
            }
    }
}
