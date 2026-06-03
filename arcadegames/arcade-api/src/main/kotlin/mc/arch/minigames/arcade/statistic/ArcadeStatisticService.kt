package mc.arch.minigames.arcade.statistic

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.statistics.IncrementalStatistic
import gg.tropic.practice.statistics.StatisticService
import mc.arch.minigames.arcade.ArcadeStatistic

/**
 * @author Subham
 * @since 7/27/25
 */
@Service
object ArcadeStatisticService
{
    @Configure
    fun configure()
    {
        StatisticService.provideCustomStatistics {
            ArcadeStatistic.entries.map { statistic ->
                IncrementalStatistic(
                    id = statistic.statisticID
                )
            }
        }
    }
}
