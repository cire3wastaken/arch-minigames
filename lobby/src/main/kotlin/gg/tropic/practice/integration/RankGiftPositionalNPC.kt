package gg.tropic.practice.integration

import gg.scala.commons.spatial.Position
import gg.scala.lemon.util.QuickAccess
import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.game.extensions.gems.rankgifting.RankGiftingPerkService
import net.evilblock.cubed.entity.EntityHandler
import net.evilblock.cubed.entity.npc.NpcEntity
import net.evilblock.cubed.util.CC
import org.bukkit.Bukkit

/**
 * Class created on 5/24/2026

 * @author Max C.
 * @project arch-minigames
 * @website https://solo.to/redis
 */
class RankGiftPositionalNPC(
    val position: Int,
    val pos: Position
) : NpcEntity(
    lines = listOf(
        "???"
    ),
    location = pos.toLocation(
        Bukkit.getWorlds().first()
    )
)
{
    init
    {
        persistent = false
    }

    fun generateLines() = listOf(
        getPositionDisplay(),
        getCurrentPlayerForPosition()?.let { QuickAccess.computeColoredName(it.first, it.first.username()).join() } ?: "",
        getCurrentPlayerForPosition()?.let { "${CC.GREEN}${it.second} Ranks!" } ?: ""
    )

    fun configure()
    {
        initializeData()
        EntityHandler.trackEntity(this)

        updateLines(generateLines())

        getCurrentPlayerForPosition()?.let {
            updateTextureByUsername(it.first.username(), { _, _ ->
                //we dont care abt the fail just let the npc die
            })
        }

        updateForCurrentWatchers()
    }

    fun getCurrentPlayerForPosition() = RankGiftingPerkService.getCachedLeaderboardPositions().getOrNull(position - 1)

    fun getPositionDisplay(): String = when (position)
    {
        1 -> "${CC.B_YELLOW}#1"
        2 -> "${CC.B_GRAY}#2"
        3 -> "${CC.B_GOLD}#3"
        else ->
        {
            "${CC.WHITE}$position"
        }
    }
}
