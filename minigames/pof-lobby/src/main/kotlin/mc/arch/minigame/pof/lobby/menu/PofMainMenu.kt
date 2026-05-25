package mc.arch.minigame.pof.lobby.menu

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.menu.StatisticsMenu
import gg.tropic.practice.minigame.joinGame
import gg.tropic.practice.profile.PracticeProfileService
import mc.arch.minigame.pof.PofGameType
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

class PofMainMenu : Menu("Pillar of Fortune Menu")
{
    override fun size(buttons: Map<Int, Button>) = 27
    override fun getButtons(player: Player) = mapOf(
        11 to ItemBuilder
            .of(XMaterial.GOLD_BLOCK)
            .name("${CC.GOLD}Welcome to Pillar of Fortune!")
            .addToLore(
                "${CC.GRAY}Survive on your pillar while",
                "${CC.GRAY}random loot rains from the sky!",
                "",
                "${CC.GRAY}Be the last one standing",
                "${CC.GRAY}to claim victory!",
                "",
                "${CC.GREEN}Click to start playing!"
            )
            .toButton { _, _ ->
                player.closeInventory()
                player.sendMessage("${CC.GRAY}Finding a game for you to join...")

                PofGameType
                    .computeGameTypeRequiringPlayers()
                    .thenAccept { metadata ->
                        metadata.joinGame(player)
                    }
            },
        15 to ItemBuilder
            .of(XMaterial.NETHER_STAR)
            .name("${CC.AQUA}Your Statistics")
            .addToLore(
                "${CC.GRAY}See how competitive you",
                "${CC.GRAY}are compared to other players!",
                "",
                "${CC.GREEN}Click to view!"
            )
            .toButton { _, _ ->
                StatisticsMenu(
                    player,
                    PracticeProfileService.find(player)!!
                ).openMenu(player)
            },
    )
}
