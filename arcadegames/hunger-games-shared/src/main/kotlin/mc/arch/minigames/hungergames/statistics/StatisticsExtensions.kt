package mc.arch.minigames.hungergames.statistics

import gg.tropic.game.extensions.profile.CorePlayerProfileService
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.statistics.valueOf
import gg.tropic.practice.extensions.toShortString
import net.evilblock.cubed.util.CC

/**
 * @author ArchMC
 */
fun PracticeProfile.formatStatistics(mode: MiniGameModeMetadata?) = listOf(
    "${CC.GRAY}Games Played: ${valueOf(
        CoreHungerGamesStatistic.PLAYS.toMode(mode)
    )}",
    "${CC.GRAY}Win Streak: ${valueOf(
        CoreHungerGamesStatistic.WIN_STREAK.toMode(mode)
    )}",
    "",
    "${CC.GRAY}Wins: ${valueOf(
        CoreHungerGamesStatistic.WINS.toMode(mode)
    )}",
    "${CC.GRAY}Losses: ${valueOf(
        CoreHungerGamesStatistic.LOSSES.toMode(mode)
    )}",
    "",
    "${CC.GRAY}Kills: ${valueOf(
        CoreHungerGamesStatistic.KILLS.toMode(mode)
    )}",
    "${CC.GRAY}Assists: ${valueOf(
        CoreHungerGamesStatistic.ASSISTS.toMode(mode)
    )}",
    "${CC.GRAY}Deaths: ${valueOf(
        CoreHungerGamesStatistic.DEATHS.toMode(mode)
    )}",
)

fun PracticeProfile.formatCoreHolographicStatistics(): List<String>
{
    val coreProfile = CorePlayerProfileService.find(identifier)
        ?: return listOf("${CC.GRAY}???")
    val level = coreProfile.getLevelInfo("hungergames")

    return listOf(
        "${CC.GRAY}Level: ${CC.GRAY}${
            level.formattedDisplay
        }",
        "${CC.GRAY}Progress: ${CC.GREEN}${
            level.currentXP.toLong().toShortString()
        }${CC.GRAY}/${CC.AQUA}${
            level.xpRequiredForNext.toLong().toShortString()
        }",
        "",
        "${CC.GRAY}Wins: ${valueOf(
            CoreHungerGamesStatistic.WINS.toCore()
        )}",
        "${CC.GRAY}Kills: ${valueOf(
            CoreHungerGamesStatistic.KILLS.toCore()
        )}",
        "${CC.GRAY}Assists: ${valueOf(
            CoreHungerGamesStatistic.ASSISTS.toCore()
        )}"
    )
}
