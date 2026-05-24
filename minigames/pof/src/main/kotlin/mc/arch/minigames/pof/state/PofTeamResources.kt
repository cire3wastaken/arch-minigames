package mc.arch.minigames.pof.state

import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.visuals.TeamVisuals
import org.bukkit.ChatColor

data class PofTeamResources(
    val gameTeam: GameTeam,
    val identifier: TeamIdentifier,
    val chatColor: ChatColor = TeamVisuals.toChatColor(identifier),
    val readableName: String = TeamVisuals.toReadable(chatColor)
)
{
    val members: Set<java.util.UUID>
        get() = gameTeam.players
}
