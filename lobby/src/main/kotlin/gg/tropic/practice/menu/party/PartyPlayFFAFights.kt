package gg.tropic.practice.menu.party

import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.kit.group.KitGroup
import gg.tropic.practice.kit.group.KitGroupService
import gg.tropic.practice.menu.TemplateKitMenu
import gg.tropic.practice.menu.TemplateMapMenu
import gg.tropic.practice.player.LobbyPlayerService
import gg.tropic.practice.queue.QueueService
import gg.tropic.practice.region.PlayerRegionFromRedisProxy
import gg.tropic.practice.region.Region
import gg.tropic.practice.suffixWhenDev
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.buttons.TexturedHeadButton
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import java.util.*

class PartyPlayFFAFights(
    viewer: Player,
    private val partyPlayers: List<UUID>
) : TemplateKitMenu(viewer)
{
    private var regionSelection: Region? = null

    init
    {
        updateAfterClick = true
    }

    override fun getGlobalButtons(player: Player) = mapOf(
        4 to ItemBuilder
            .copyOf(
                object : TexturedHeadButton(Constants.GLOBE_ICON)
                {}.getButtonItem(player)
            )
            .name("${CC.GREEN}Region")
            .addToLore(
                "${
                    if (regionSelection == null) CC.B_WHITE else CC.GRAY
                }Closest ${CC.D_GRAY}${
                    Constants.THIN_VERTICAL_LINE
                } ${
                    if (regionSelection == Region.NA) CC.B_WHITE else CC.GRAY
                }NA ${CC.D_GRAY}${
                    Constants.THIN_VERTICAL_LINE
                } ${
                    if (regionSelection == Region.EU) CC.B_WHITE else CC.GRAY
                }EU"
            )
            .toButton { _, _ ->
                regionSelection = when (regionSelection)
                {
                    null -> Region.NA
                    Region.NA -> Region.EU
                    Region.EU -> null
                    Region.Both -> null
                }

                Button.playNeutral(player)
            }
    )

    override fun getPrePaginatedTitle(player: Player) = "Select a kit..."

    override fun filterDisplayOfKit(player: Player, kit: Kit) =
        kit.lifecycleType == GameLifecycle.SoulBound

    override fun itemTitleFor(player: Player, kit: Kit) = "${CC.B_GREEN}${kit.displayName}"
    override fun itemDescriptionOf(player: Player, kit: Kit) = listOf(
        "",
        "${CC.GREEN}Click to select!"
    )

    override fun shouldIncludeKitDescription() = false

    override fun itemClicked(player: Player, kit: Kit, type: ClickType)
    {
        val mapSelectionStage = object : TemplateMapMenu()
        {
            override fun filterDisplayOfMap(map: gg.tropic.practice.map.Map) = map.associatedKitGroups
                .intersect(
                    KitGroupService.groupsOf(kit)
                        .map(KitGroup::id)
                        .toSet()
                )
                .isNotEmpty()

            override fun itemDescriptionOf(player: Player, map: gg.tropic.practice.map.Map) = listOf(
                "",
                "${CC.GREEN}Click to select!"
            )

            override fun itemClicked(player: Player, map: gg.tropic.practice.map.Map, type: ClickType)
            {
                Button.playNeutral(player)
                startMatchWithMapKitCombo(player, kit, map)
            }

            override fun getGlobalButtons(player: Player) = mutableMapOf(
                4 to ItemBuilder
                    .of(Material.NETHER_STAR)
                    .name("${CC.B_AQUA}Random Map")
                    .addToLore(
                        "",
                        "${CC.AQUA}Click to select!"
                    )
                    .toButton { _, _ ->
                        Button.playNeutral(player)
                        startMatchWithMapKitCombo(player, kit, getAvailableMaps().random())
                    }
            )

            override fun getPrePaginatedTitle(player: Player) = "Select a map..."
        }

        if (player.hasPermission("practice.party.play.select-custom-map"))
        {
            if (!mapSelectionStage.ensureMapsAvailable())
            {
                Button.playFail(player)
                player.sendMessage("${CC.RED}There are no maps associated with the kit ${CC.YELLOW}${kit.displayName}${CC.RED}!")
                return
            }

            Button.playNeutral(player)
            mapSelectionStage.openMenu(player)
            return
        }

        Button.playNeutral(player)
        startMatchWithMapKitCombo(
            player, kit, mapSelectionStage.getAvailableMaps().random()
        )
    }

    private fun startMatchWithMapKitCombo(
        player: Player, kit: Kit, map: gg.tropic.practice.map.Map
    )
    {
        val gameExpectation = GameExpectation(
            UUID.randomUUID(),
            players = partyPlayers.toMutableSet(),
            teams = setOf(
                GameTeam(TeamIdentifier.A, partyPlayers.toMutableSet())
            ),
            kitId = kit.id,
            mapId = map.name,
            queueId = "party",
            freeForAll = true
        )

        player.closeInventory()
        val lobbyPlayer = LobbyPlayerService.find(player)
            ?: return run {
                player.sendMessage("${CC.RED}You are unable to do this right now!")
            }

        if (!lobbyPlayer.isInParty())
        {
            player.sendMessage("${CC.RED}You are no longer in a party!")
            return
        }

        lobbyPlayer.partyOf().onlinePlayers()
            .let { onlinePlayers ->
                if (!gameExpectation.players.all { member -> member in onlinePlayers })
                {
                    player.sendMessage("${CC.RED}One or more of your party members are unavailable to play in this Free-For-All game.")
                    return@let
                }

                val playerRegion = PlayerRegionFromRedisProxy
                    .of(player)
                    .join()

                QueueService.createMessage(
                    "create-match",
                    "config" to gameExpectation,
                    "map" to map.name,
                    "kit" to kit.id,
                    "region" to (regionSelection ?: playerRegion).name
                ).publish(
                    channel = "communications-gamequeue".suffixWhenDev()
                )

                Button.playSuccess(player)
                player.sendMessage("${CC.B_GREEN}We're preparing your match...")
            }
    }
}
