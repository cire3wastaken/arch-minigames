package mc.arch.minigame.pof.services

import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.statistics.StatisticID
import gg.tropic.practice.statistics.StatisticLifetime
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIdFrom

enum class CorePofStatistic(
    private val toStatisticID: (MiniGameModeMetadata?) -> StatisticID,
    val lifetime: StatisticLifetime? = null,
)
{
    PLAYS({ mode ->
        statisticIdFrom(TrackedKitStatistic.Plays) {
            if (mode != null) kit(mode.kitID) else kit("pof")
        }
    }),
    PLAYS_DAILY({ mode ->
        statisticIdFrom(TrackedKitStatistic.Plays) {
            daily()
            if (mode != null) kit(mode.kitID) else kit("pof")
        }
    }),

    KILLS({ mode ->
        statisticIdFrom(TrackedKitStatistic.Kills) {
            if (mode != null) kit(mode.kitID) else kit("pof")
        }
    }),
    KILLS_DAILY({ mode ->
        statisticIdFrom(TrackedKitStatistic.Kills) {
            daily()
            if (mode != null) kit(mode.kitID) else kit("pof")
        }
    }, StatisticLifetime.Daily),
    DEATHS({ mode ->
        statisticIdFrom(TrackedKitStatistic.Deaths) {
            if (mode != null) kit(mode.kitID) else kit("pof")
        }
    }),

    WIN_STREAK({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:winstreak:lifetime")
    }),

    WINS({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:wins:lifetime")
    }),
    WINS_DAILY({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:wins:daily", lifetime = StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),
    WINS_WEEKLY({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:wins:weekly", lifetime = StatisticLifetime.Weekly)
    }, StatisticLifetime.Weekly),

    LOSSES({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:losses:lifetime")
    }),

    BLOCKS_PLACED({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:blocks-placed:lifetime")
    }),
    LOOT_PICKED_UP({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:loot-picked-up:lifetime")
    }),
    LOOT_PICKED_UP_WEEKLY({ mode ->
        StatisticID.fromCustom("pof:${mode?.id ?: "core"}:loot-picked-up:weekly", lifetime = StatisticLifetime.Weekly)
    }, StatisticLifetime.Weekly);

    fun toCore() = toStatisticID(null)
    fun toMode(mode: MiniGameModeMetadata?) = toStatisticID(mode)
}
