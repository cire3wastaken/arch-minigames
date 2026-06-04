package mc.arch.minigames.arcade.rlgl

import org.bukkit.Bukkit
import java.util.UUID

data class RlglPlayerResources(
    val username: String,
    val player: UUID,
    var spectator: Boolean = false,
    var lastSpawnTime: Long = 0L,
    var crossedFinishAt: Long = 0L,
    var finishPlace: Int = 0,
    var kills: Int = 0
)
{
    fun toPlayer() = Bukkit.getPlayer(player)
}
