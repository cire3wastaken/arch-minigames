package mc.arch.minigames.pof.state

import gg.tropic.practice.games.team.TeamIdentifier
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

data class PofPlayerResources(
    val username: String,
    val player: UUID,
    var team: TeamIdentifier? = null,
    var kills: Int = 0,
    var deaths: Int = 0,
    var blocksPlaced: Int = 0,
    var lootPickedUp: Int = 0,
    var spectator: Boolean = false,
    var lastSpawnTime: Long = 0L,
    var survivedUntil: Long = 0L
)
{
    fun toPlayer(): Player? = Bukkit.getPlayer(player)
}
