package gg.tropic.practice.menu.manage

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.configurable.editPosition
import gg.scala.commons.configurable.editString
import gg.scala.commons.configurable.editStringList
import gg.scala.commons.spatial.Position
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.integration.RankGiftLeaderboardHologram
import gg.tropic.practice.menu.manage.bezierteleporters.ViewBezierTeleportersMenu
import gg.tropic.practice.menu.manage.leaderboards.ViewMinigameLeaderboardsMenu
import gg.tropic.practice.menu.manage.levitationportals.ViewLevitationPortalsMenu
import gg.tropic.practice.menu.manage.lobbynpc.ViewMinigameLobbyNPCsMenu
import gg.tropic.practice.menu.manage.npc.ViewMinigameNPCsMenu
import gg.tropic.practice.menu.manage.quests.ViewQuestsMenu
import gg.tropic.practice.menu.manage.scoreboard.ManageScoreboardMenu
import gg.tropic.practice.menu.manage.top3.ViewTop3LeaderboardsMenu
import gg.tropic.practice.parkour.ManageParkourMenu
import gg.tropic.practice.parkour.ParkourLeaderboardHologram
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.Color
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

/**
 * @author Subham
 * @since 6/27/25
 */
class ManageLobbyMenu : Menu("Managing Minigame Lobby")
{
    init
    {
        placeholder = true
    }

    override fun getButtons(player: Player) = mapOf(
        10 to editPosition(
            PracticeConfigurationService,
            title = "Spawn Location",
            material = XMaterial.EMERALD,
            getter = {
                local().spawnLocation
            },
            setter = {
                local().spawnLocation = it
            }
        ),
        11 to editStringList(
            PracticeConfigurationService,
            title = "Login MOTD",
            material = XMaterial.OAK_SIGN,
            getter = {
                local().loginMOTD
            },
            setter = {
                local().loginMOTD = Color.translate(it)
            }
        ),
        12 to ItemBuilder
            .of(XMaterial.STONE_PRESSURE_PLATE)
            .name("${CC.YELLOW}Parkour")
            .addToLore(
                "${CC.GRAY}Click to edit parkour"
            )
            .toButton { _, _ ->
                ManageParkourMenu().openMenu(player)
            },
        13 to ItemBuilder
            .of(XMaterial.CHEST)
            .name("${CC.YELLOW}Manage Top3s")
            .addToLore(
                "${CC.I_WHITE}Only supported in minigame lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewTop3LeaderboardsMenu().openMenu(player)
            },
        14 to ItemBuilder
            .of(XMaterial.EGG)
            .name("${CC.YELLOW}Manage Play NPCs")
            .addToLore(
                "${CC.I_WHITE}Only supported in minigame lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewMinigameNPCsMenu().openMenu(player)
            },
        15 to ItemBuilder
            .of(XMaterial.PRISMARINE_SHARD)
            .name("${CC.YELLOW}Manage Leaderboards")
            .addToLore(
                "${CC.I_WHITE}Only supported in minigame lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewMinigameLeaderboardsMenu().openMenu(player)
            },
        16 to ItemBuilder
            .of(XMaterial.VILLAGER_SPAWN_EGG)
            .name("${CC.YELLOW}Manage Lobby NPCs")
            .addToLore(
                "${CC.I_WHITE}Supported in all lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewMinigameLobbyNPCsMenu().openMenu(player)
            },
        19 to ItemBuilder
            .of(XMaterial.MAP)
            .name("${CC.YELLOW}Manage Quests")
            .addToLore(
                "${CC.I_WHITE}Supported in all lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewQuestsMenu().openMenu(player)
            },
        20 to ItemBuilder
            .of(XMaterial.FEATHER)
            .name("${CC.YELLOW}Bezier Teleporters")
            .addToLore(
                "${CC.I_WHITE}Supported in all lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewBezierTeleportersMenu().openMenu(player)
            },
        21 to editPosition(
            PracticeConfigurationService,
            title = "Quest Master Position",
            material = XMaterial.ZOMBIE_VILLAGER_SPAWN_EGG,
            getter = {
                local().questMasterLocation
            },
            setter = {
                local().questMasterLocation = it
            }
        ),
        22 to editPosition(
            PracticeConfigurationService,
            title = "Core Holographic Stats Position",
            material = XMaterial.LIGHT_BLUE_STAINED_GLASS,
            getter = {
                local().coreHolographicStatsPosition
            },
            setter = {
                local().coreHolographicStatsPosition = it
            }
        ),
        23 to editString(
            PracticeConfigurationService,
            title = "External Player Count BaseURL",
            material = XMaterial.COMPARATOR,
            getter = {
                externalPlayerCountBaseUrl
            },
            setter = {
                externalPlayerCountBaseUrl = it
            }
        ),
        24 to ItemBuilder
            .of(XMaterial.OAK_SIGN)
            .name("${CC.YELLOW}Scoreboard")
            .addToLore(
                "${CC.I_WHITE}Supported in all lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ManageScoreboardMenu().openMenu(player)
            },
        25 to ItemBuilder
            .of(XMaterial.END_PORTAL_FRAME)
            .name("${CC.YELLOW}Portals")
            .addToLore(
                "${CC.I_WHITE}Supported in all lobbies",
                "${CC.GRAY}Click to manage"
            )
            .toButton { _, _ ->
                ViewLevitationPortalsMenu().openMenu(player)
            },
        28 to editPosition(
            PracticeConfigurationService,
            title = "Rank Gift Leaderboard Location",
            material = XMaterial.ENDER_CHEST,
            getter = {
                local().rankGiftLeaderboardLocation ?: Position(0.0, 0.0, 0.0)
            },
            setter = {
                local().rankGiftLeaderboardLocation = it
                val hologram = RankGiftLeaderboardHologram(player.location)

                hologram.configure()
            }
        ),
        29 to editPosition(
            PracticeConfigurationService,
            title = "Rank Gift NPC #1",
            material = XMaterial.DIAMOND,
            getter = {
                local().rankGiftTop3NPCs?.getOrNull(0) ?: Position(0.0, 0.0, 0.0)
            },
            setter = {
                val current = local().rankGiftTop3NPCs ?: mutableListOf()
                while (current.size <= 0) current.add(null)
                current[0] = it
                local().rankGiftTop3NPCs = current
            }
        ),
        30 to editPosition(
            PracticeConfigurationService,
            title = "Rank Gift NPC #2",
            material = XMaterial.IRON_INGOT,
            getter = {
                local().rankGiftTop3NPCs?.getOrNull(1) ?: Position(0.0, 0.0, 0.0)
            },
            setter = {
                val current = local().rankGiftTop3NPCs ?: mutableListOf()
                while (current.size <= 1) current.add(null)
                current[1] = it
                local().rankGiftTop3NPCs = current
            }
        ),
        31 to editPosition(
            PracticeConfigurationService,
            title = "Rank Gift NPC #3",
            material = XMaterial.GOLD_INGOT,
            getter = {
                local().rankGiftTop3NPCs?.getOrNull(2) ?: Position(0.0, 0.0, 0.0)
            },
            setter = {
                val current = local().rankGiftTop3NPCs ?: mutableListOf()
                while (current.size <= 2) current.add(null)
                current[2] = it
                local().rankGiftTop3NPCs = current
            }
        ),
    )

    override fun size(buttons: Map<Int, Button>) = 45
}
