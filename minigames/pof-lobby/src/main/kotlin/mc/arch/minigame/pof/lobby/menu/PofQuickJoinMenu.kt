package mc.arch.minigame.pof.lobby.menu

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.joinGame
import gg.tropic.practice.minigame.toConciseJoinButton
import mc.arch.minigame.pof.PofGameType
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

class PofQuickJoinMenu : Menu("Join a Pillar of Fortune game")
{
    override fun size(buttons: Map<Int, Button>) = 27
    override fun getButtons(player: Player) = mapOf(
        11 to PofGameType
            .mode("solo")
            .toConciseJoinItem()
            .toConciseJoinButton("solo"),

        13 to PofGameType
            .mode("duos")
            .toConciseJoinItem()
            .toConciseJoinButton("duos"),

        15 to ItemBuilder
            .of(XMaterial.EMERALD)
            .name("${CC.GREEN}Quick Join")
            .addToLore(
                "${CC.GRAY}Join any game that is",
                "${CC.GRAY}about to start!",
                "",
                "${CC.YELLOW}Click to join!"
            )
            .toButton { _, _ ->
                player.closeInventory()
                player.sendMessage("${CC.GRAY}Finding a game for you to join...")

                PofGameType
                    .computeGameTypeRequiringPlayers()
                    .thenAccept { metadata ->
                        metadata.joinGame(player)
                    }
            }
    )
}
