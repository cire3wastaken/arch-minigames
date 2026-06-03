package gg.tropic.practice.menu.party

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import gg.tropic.practice.player.LobbyPlayerService
import mc.arch.minigames.parties.service.NetworkPartyService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Menu for selecting which minigame to play in Private Games mode.
 * Shows all available minigame types and teleports the party to that lobby.
 *
 * @author GrowlyX
 * @since 12/21/24
 */
class PrivateGamesMinigameSelectorMenu : Menu("Private Games - Select Mode")
{
    init
    {
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>) = 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        // Get all registered minigame types
        val minigameTypes = listOf(
            MinigameTypeInfo("bedwars", "BedWars", XMaterial.RED_BED, "bwlobby"),
            MinigameTypeInfo("skywars", "SkyWars", XMaterial.FEATHER, "swlobby"),
            MinigameTypeInfo("arcade", "Arcade", XMaterial.GOLDEN_APPLE, "arcadelobby")
        )

        minigameTypes.forEachIndexed { index, info ->
            val lobbyServers = ServerContainer
                .getServersInGroupCasted<GameServer>(info.lobbyGroup)

            val isAvailable = lobbyServers.isNotEmpty()
            val playerCount = lobbyServers.sumOf { it.getPlayersCount() ?: 0 }

            buttons[10 + index * 2] = ItemBuilder
                .of(info.icon)
                .name("${CC.GREEN}${info.displayName}")
                .addToLore(
                    "${CC.GRAY}Play ${info.displayName} with",
                    "${CC.GRAY}your party in a private match!",
                    "",
                    "${CC.GRAY}In Lobby: ${CC.AQUA}$playerCount",
                    ""
                )
                .apply {
                    if (isAvailable)
                    {
                        addToLore("${CC.YELLOW}Click to play!")
                    } else
                    {
                        addToLore("${CC.RED}Currently unavailable!")
                    }
                }
                .toButton { _, _ ->
                    if (!isAvailable)
                    {
                        player.sendMessage("${CC.RED}This minigame is currently unavailable!")
                        return@toButton
                    }

                    val lobbyPlayer = LobbyPlayerService.find(player)
                        ?: return@toButton

                    if (!lobbyPlayer.isInParty())
                    {
                        player.sendMessage("${CC.RED}You are no longer in a party!")
                        player.closeInventory()
                        return@toButton
                    }

                    val party = lobbyPlayer.partyOf()

                    // Find best lobby server
                    val targetServer = lobbyServers
                        .filter { it.getPlayersCount() != null }
                        .minByOrNull { it.getPlayersCount()!! }
                        ?: lobbyServers.firstOrNull()

                    if (targetServer == null)
                    {
                        player.sendMessage("${CC.RED}No ${info.displayName} lobby servers are available!")
                        return@toButton
                    }

                    player.closeInventory()
                    player.sendMessage("${CC.GREEN}Warping your party to ${info.displayName} lobby...")

                    // Warp entire party to the minigame lobby
                    CompletableFuture.runAsync {
                        NetworkPartyService.warpPartyTo(party.delegate, targetServer.id)
                    }
                }
        }

        return buttons
    }

    private data class MinigameTypeInfo(
        val internalId: String,
        val displayName: String,
        val icon: XMaterial,
        val lobbyGroup: String
    )
}
