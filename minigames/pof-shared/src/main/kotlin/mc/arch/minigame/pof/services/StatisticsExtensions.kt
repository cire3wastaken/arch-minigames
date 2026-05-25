package mc.arch.minigame.pof.services

import gg.tropic.game.extensions.profile.CorePlayerProfileService
import gg.tropic.practice.extensions.toShortString
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.statistics.valueOf
import net.evilblock.cubed.util.CC

fun PracticeProfile.formatStatistics(mode: MiniGameModeMetadata?) = listOf(
    "${CC.GRAY}Games Played: ${valueOf(
        CorePofStatistic.PLAYS.toMode(mode)
    )}",
    "${CC.GRAY}Win Streak: ${valueOf(
        CorePofStatistic.WIN_STREAK.toMode(mode)
    )}",
    "",
    "${CC.GRAY}Lifetime Wins: ${valueOf(
        CorePofStatistic.WINS.toMode(mode)
    )}",
    "${CC.GRAY}Weekly Wins: ${valueOf(
        CorePofStatistic.WINS_WEEKLY.toMode(mode)
    )}",
    "${CC.GRAY}Daily Wins: ${valueOf(
        CorePofStatistic.WINS_DAILY.toMode(mode)
    )}",
    "${CC.GRAY}Losses: ${valueOf(
        CorePofStatistic.LOSSES.toMode(mode)
    )}",
    "",
    "${CC.GRAY}Kills: ${valueOf(
        CorePofStatistic.KILLS.toMode(mode)
    )}",
    "${CC.GRAY}Deaths: ${valueOf(
        CorePofStatistic.DEATHS.toMode(mode)
    )}",
    "",
    "${CC.GRAY}Blocks Placed: ${valueOf(
        CorePofStatistic.BLOCKS_PLACED.toMode(mode)
    )}",
    "${CC.GRAY}Loot Picked Up: ${valueOf(
        CorePofStatistic.LOOT_PICKED_UP.toMode(mode)
    )}"
)

fun PracticeProfile.formatCoreHolographicStatistics(): List<String>
{
    val coreProfile = CorePlayerProfileService.find(identifier)
        ?: return listOf("${CC.GRAY}???")
    val level = coreProfile.getLevelInfo("pof")

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
        "${CC.GRAY}Win Streak: ${valueOf(
            CorePofStatistic.WIN_STREAK.toCore()
        )}",
        "${CC.GRAY}Lifetime Wins: ${valueOf(
            CorePofStatistic.WINS.toCore()
        )}",
        "${CC.GRAY}Lifetime Kills: ${valueOf(
            CorePofStatistic.KILLS.toCore()
        )}",
    )
}
