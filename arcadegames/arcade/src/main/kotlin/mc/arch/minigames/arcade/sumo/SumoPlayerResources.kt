package mc.arch.minigames.arcade.sumo

import org.bukkit.Bukkit
import java.util.UUID

/**
 * @author Subham
 * @since 7/26/25
 */
data class SumoPlayerResources(
    val username: String,
    val player: UUID,
    var eliminated: Boolean = false,
    var roundWins: Int = 0
)
{
    fun toPlayer() = Bukkit.getPlayer(player)
}
