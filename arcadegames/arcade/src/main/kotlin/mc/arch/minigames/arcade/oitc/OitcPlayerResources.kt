package mc.arch.minigames.arcade.oitc

import org.bukkit.Bukkit
import java.util.UUID

data class OitcPlayerResources(
    val username: String,
    val player: UUID,
    var kills: Int = 0,
    var deaths: Int = 0,
    var spectator: Boolean = false,
    var pendingRespawn: Boolean = false,
    var lastSpawnTime: Long = 0L
)
{
    fun toPlayer() = Bukkit.getPlayer(player)
}
