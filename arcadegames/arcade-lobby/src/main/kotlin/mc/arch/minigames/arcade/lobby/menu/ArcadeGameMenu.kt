package mc.arch.minigames.arcade.lobby.menu

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.metadata.SystemMetadataService
import gg.tropic.practice.minigame.joinMinigameQueue
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player

class ArcadeGameMenu(
    private val card: ArcadeCard
) : Menu("${card.cardName} Queue")
{
    companion object
    {
        fun queue(player: Player, cardName: String, mode: ArcadeCardMode)
        {
            player.closeInventory()
            joinMinigameQueue(player, mode.queueId, "$cardName ${mode.displayName}", mode.providerVersion)
        }
    }

    override fun size(buttons: Map<Int, Button>) = 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        val rowOne = listOf(11, 12, 13, 14, 15)
        val rowTwo = listOf(20, 21, 22, 23, 24)
        val slots = (rowOne + rowTwo).take(card.modes.size).let {
            centeredSlotLayout(it, card.modes.size)
        }

        card.modes.forEachIndexed { idx, mode ->
            buttons[slots[idx]] = modeButton(player, mode)
        }

        return buttons
    }

    private fun centeredSlotLayout(slots: List<Int>, count: Int): List<Int>
    {
        return when (count)
        {
            1 -> listOf(13)
            2 -> listOf(12, 14)
            3 -> listOf(11, 13, 15)
            4 -> listOf(10, 12, 14, 16)
            5 -> listOf(9, 11, 13, 15, 17)
            else -> slots
        }
    }

    private fun modeButton(player: Player, mode: ArcadeCardMode): Button = ItemBuilder
        .of(XMaterial.GREEN_DYE)
        .name("${CC.BL_PURPLE}${mode.displayName}")
        .apply { mode.lore.forEach { addToLore("${CC.GRAY}$it") } }
        .addToLore(
            "",
            "${CC.LIGHT_PURPLE}Click to play!",
            "${CC.GRAY}${Numbers.format(currentlyPlaying(mode))} currently playing",
        )
        .toButton { _, _ ->
            Button.playNeutral(player)
            queue(player, card.cardName, mode)
        }

    private fun currentlyPlaying(mode: ArcadeCardMode): Int = SystemMetadataService
        .allGames()
        .filter { it.queueId == mode.queueId }
        .sumOf { it.onlinePlayers ?: 0 }
}
