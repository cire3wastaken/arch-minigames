package mc.arch.minigames.pof.services

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.statistics.ExpiringStatistic
import gg.tropic.practice.statistics.IncrementalStatistic
import gg.tropic.practice.statistics.Statistic
import gg.tropic.practice.statistics.StatisticService
import mc.arch.minigames.pof.PofGameType

@Service
object PofStatisticRegistrarService
{
    @Configure
    fun configure()
    {
        StatisticService.provideCustomStatistics {
            val statistics = mutableListOf<Statistic>()
            CorePofStatistic.entries.forEach { statistic ->
                if (statistic.lifetime != null)
                {
                    statistics += ExpiringStatistic(
                        id = statistic.toCore(),
                        lifetime = statistic.lifetime,
                        defaultValue = 0L
                    )
                    return@forEach
                }

                statistics += IncrementalStatistic(
                    id = statistic.toCore(),
                    defaultValue = 0L
                )
            }

            PofGameType.gameModes.values.forEach { mode ->
                CorePofStatistic.entries.forEach { statistic ->
                    if (statistic.lifetime != null)
                    {
                        statistics += ExpiringStatistic(
                            id = statistic.toMode(mode),
                            lifetime = statistic.lifetime,
                            defaultValue = 0L
                        )
                        return@forEach
                    }

                    statistics += IncrementalStatistic(
                        id = statistic.toMode(mode),
                        defaultValue = 0L
                    )
                }
            }

            return@provideCustomStatistics statistics
        }
    }
}
