package gg.tropic.practice.minigame.holographicstats

import gg.scala.commons.spatial.Position
import gg.tropic.practice.minigame.MinigameLobby
import net.evilblock.cubed.entity.hologram.personalized.PersonalizedHologramEntity
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * @author Subham
 * @since 7/15/25
 */
class CoreHolographicStatsHologramEntity(
    position: Position
) : PersonalizedHologramEntity(
    position.toLocation(Bukkit.getWorlds().first())
)
{
    init
    {
        persistent = false
    }

    override fun getNewLines(player: Player): List<String>
    {
        val customizer = MinigameLobby.competitiveFor(player)
        val titleColor = customizer?.holographicTitleColor() ?: CC.B_WHITE
        val tickColor = customizer?.holographicTickColor() ?: CC.PRI
        val footerColor = customizer?.holographicFooterColor() ?: CC.WHITE

        return listOf(
            "${titleColor}PERSONAL STATS",
            "",
            *(customizer
                ?.holographicStatsProvider(player)
                ?: listOf("${CC.GRAY}???"))
                .map { text ->
                    if (text.isBlank())
                    {
                        return@map ""
                    }

                    return@map "${tickColor}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}$text"
                }
                .toTypedArray(),
            "",
            "${footerColor}Use /stats to view more!"
        )
    }

    override fun getUpdateInterval() = 1000L
}
