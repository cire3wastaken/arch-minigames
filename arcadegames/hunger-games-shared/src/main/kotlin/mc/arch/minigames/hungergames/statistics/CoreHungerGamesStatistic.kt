package mc.arch.minigames.hungergames.statistics

import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.statistics.StatisticID
import gg.tropic.practice.statistics.StatisticLifetime

/**
 * @author ArchMC
 */
enum class CoreHungerGamesStatistic(
    private val toStatisticID: (MiniGameModeMetadata?) -> StatisticID,
    val lifetime: StatisticLifetime? = null,
)
{
    PLAYS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:plays:lifetime")
    }),
    LOSSES({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:losses:lifetime")
    }),

    WIN_STREAK({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:winstreak:lifetime")
    }),

    WINS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:wins:lifetime")
    }),
    DAILY_WINS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:wins:daily", StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),

    DEATHS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:deaths:lifetime")
    }),

    KILLS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:kills:lifetime")
    }),
    DAILY_KILLS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:kills:daily", StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),

    ASSISTS({ mode ->
        StatisticID.fromCustom("arcade:hungergames:${mode?.id ?: "core"}:assists:lifetime")
    });

    fun toCore() = toStatisticID(null)
    fun toMode(mode: MiniGameModeMetadata?) = toStatisticID(mode)
}
