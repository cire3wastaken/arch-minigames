package gg.tropic.practice.menu.party

import gg.tropic.practice.player.LobbyPlayerService
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 2/9/2024
 */
class PartyPlayGameSelectMenu() : Menu("Select a game")
{
    init
    {
        placeholder = true
    }

    override fun getButtons(player: Player) = mapOf(
        3 to ItemBuilder
            .of(Material.GOLD_SWORD)
            .name("${CC.AQUA}Team vs. Team Fights")
            .addToLore(
                "${CC.GRAY}Create two teams with your",
                "${CC.GRAY}party members and fight",
                "${CC.GRAY}against one another!",
                "",
                "${CC.GREEN}Click to play!"
            )
            .toButton { _, _ ->
                val lobbyPlayer = LobbyPlayerService.find(player)
                    ?: return@toButton

                player.closeInventory()
                if (!lobbyPlayer.isInParty())
                {
                    player.sendMessage("${CC.RED}You are no longer in a party!")
                    return@toButton
                }

                val players = lobbyPlayer.partyOf().onlinePlayers()
                if (players.size < 2)
                {
                    player.sendMessage("${CC.RED}You must have at least two players in your party to start a Team vs. Team fight!")
                    return@toButton
                }

                PartyPlayTVTFights(players.toList()).openMenu(player)
            },
        4 to ItemBuilder
            .of(Material.IRON_SWORD)
            .name("${CC.RED}Free-For-All Fights")
            .addToLore(
                "${CC.GRAY}Everyone for themselves!",
                "${CC.GRAY}All party members fight in",
                "${CC.GRAY}one arena — last player",
                "${CC.GRAY}standing wins!",
                "",
                "${CC.GREEN}Click to play!"
            )
            .toButton { _, _ ->
                val lobbyPlayer = LobbyPlayerService.find(player)
                    ?: return@toButton

                player.closeInventory()
                if (!lobbyPlayer.isInParty())
                {
                    player.sendMessage("${CC.RED}You are no longer in a party!")
                    return@toButton
                }

                val players = lobbyPlayer.partyOf().onlinePlayers()
                if (players.size < 2)
                {
                    player.sendMessage("${CC.RED}You must have at least two players in your party to start a Free-For-All fight!")
                    return@toButton
                }

                PartyPlayFFAFights(player, players.toList()).openMenu(player)
            },
        5 to ItemBuilder
            .of(Material.ENDER_PORTAL_FRAME)
            .name("${CC.GOLD}Robot Fights")
            .addToLore(
                "${CC.GRAY}Your team versus bots!",
                "",
                "${CC.GREEN}Click to play!"
            )
            .toButton { _, _ ->
                val lobbyPlayer = LobbyPlayerService.find(player)
                    ?: return@toButton

                player.closeInventory()
                if (!lobbyPlayer.isInParty())
                {
                    player.sendMessage("${CC.RED}You are no longer in a party!")
                    return@toButton
                }

                player.sendMessage("${CC.RED}This feature is currently disabled!")

                /*lobbyPlayer.partyOf()
                    .onlinePracticePlayersInLobby()
                    .thenAccept {
                        if (it.keys.size < 2)
                        {
                            player.sendMessage("${CC.RED}You must have at least two players in your party to start a Robot fight!")
                            return@thenAccept
                        }

                        PartyPlayBotsIntegration.createBotsWith(player, it.keys)
                    }*/
            }
    )
}
