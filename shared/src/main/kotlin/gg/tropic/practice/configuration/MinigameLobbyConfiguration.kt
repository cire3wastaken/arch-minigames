package gg.tropic.practice.configuration

import gg.scala.commons.spatial.Position
import gg.tropic.practice.configuration.minigame.MinigameBezierTeleporter
import gg.tropic.practice.configuration.minigame.MinigameLeaderboard
import gg.tropic.practice.configuration.minigame.MinigameLobbyNPC
import gg.tropic.practice.configuration.minigame.MinigamePlayNPC
import gg.tropic.practice.configuration.minigame.MinigameTopPlayerNPCSet
import gg.tropic.practice.configuration.minigame.levitationportal.LevitationPortalSpec
import gg.tropic.practice.parkour.ParkourConfiguration
import gg.tropic.practice.quests.model.Quest
import net.evilblock.cubed.util.CC

/**
 * @author Subham
 * @since 6/24/25
 */
data class MinigameLobbyConfiguration(
    var spawnLocation: Position = Position(
        0.0, 100.0, 0.0, 180.0F, 0.0F
    ),
    var coreHolographicStatsPosition: Position = Position(
        0.0, 100.0, 0.0, 180.0F, 0.0F
    ),
    var loginMOTD: MutableList<String> = mutableListOf(
        "",
        "${CC.B_PRI}Welcome to Tropic Practice",
        "${CC.GRAY}We are currently in BETA! Report bugs in our Discord.",
        ""
    ),
    var parkourConfiguration: ParkourConfiguration = ParkourConfiguration(),
    val playNPCs: MutableList<MinigamePlayNPC> = mutableListOf(),
    var bezierTeleporters: MutableList<MinigameBezierTeleporter> = mutableListOf(),
    val topPlayerNPCSets: MutableList<MinigameTopPlayerNPCSet> = mutableListOf(),
    val leaderboards: MutableList<MinigameLeaderboard> = mutableListOf(),
    var minigameLobbyNPCs: MutableList<MinigameLobbyNPC> = mutableListOf(),
    var levitationPortals: MutableList<LevitationPortalSpec> = mutableListOf(),
    var quests: MutableMap<String, Quest> = mutableMapOf(),
    var rankGiftLeaderboardLocation: Position? = null,
    var rankGiftTop3NPCs: MutableList<Position?>? = mutableListOf(),
    var questMasterLocation: Position = Position(
        0.0, 0.0, 0.0, 180.0F, 0.0F
    )
)
{
    fun isParkourReady() = parkourConfiguration.isReady()
}
