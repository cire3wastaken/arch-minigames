package gg.tropic.practice.minigame.menu

import gg.tropic.practice.minigame.MiniGameModeMetadata
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player

/**
 * Picker shown when a single play NPC bundles multiple modes of the same game
 * (e.g. Mini Walls solo + squads). Selecting a mode opens the regular
 * [MinigameNPCPlayMenu] for that mode, preserving map selection / rejoins.
 */
data class MinigameNPCModeSelectorMenu(
    private val title: String,
    private val modes: List<MiniGameModeMetadata>
) : Menu("Play $title")
{
    override fun size(buttons: Map<Int, Button>) = 27

    private fun Player.modeItem(mode: MiniGameModeMetadata) = mode.toItem()
        .addToLore(
            "",
            "${CC.YELLOW}Click to select!"
        )
        .toButton { _, _ ->
            Button.playNeutral(this)
            MinigameNPCPlayMenu(mode).openMenu(this)
        }

    private fun centeredSlotLayout(count: Int): List<Int> = when (count)
    {
        1 -> listOf(13)
        2 -> listOf(12, 14)
        3 -> listOf(11, 13, 15)
        4 -> listOf(11, 13, 14, 15)
        else -> listOf(11, 12, 13, 14, 15)
    }

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val slots = centeredSlotLayout(modes.size)
        return modes.take(slots.size).mapIndexed { index, mode ->
            slots[index] to player.modeItem(mode)
        }.toMap()
    }
}
