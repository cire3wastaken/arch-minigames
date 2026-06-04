package mc.arch.minigames.hungergames.game.resources

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID

/**
 * @author ArchMC
 */
data class PlayerResources(
    val player: UUID,
    val playerName: String,
    val expectedSpectator: Boolean,
    val preferredPrefixedName: String
)
{
    var deaths = 0
    var kills = 0
    var assists = 0

    fun toPlayer(): Player? = Bukkit.getPlayer(player)
}
