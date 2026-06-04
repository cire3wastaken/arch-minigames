package mc.arch.minigames.pof.services

import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.statistics.StatisticID
import gg.tropic.practice.statistics.StatisticLifetime

enum class CorePofStatistic(
    private val toStatisticID: (MiniGameModeMetadata?) -> StatisticID,
    val lifetime: StatisticLifetime? = null,
)
{
    PLAYS({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:plays:lifetime")
    }),
    PLAYS_DAILY({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:plays:daily", lifetime = StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),

    KILLS({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:kills:lifetime")
    }),
    KILLS_DAILY({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:kills:daily", lifetime = StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),
    DEATHS({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:deaths:lifetime")
    }),

    WIN_STREAK({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:winstreak:lifetime")
    }),

    WINS({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:wins:lifetime")
    }),
    WINS_DAILY({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:wins:daily", lifetime = StatisticLifetime.Daily)
    }, StatisticLifetime.Daily),
    WINS_WEEKLY({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:wins:weekly", lifetime = StatisticLifetime.Weekly)
    }, StatisticLifetime.Weekly),

    LOSSES({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:losses:lifetime")
    }),

    BLOCKS_PLACED({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:blocks-placed:lifetime")
    }),
    LOOT_PICKED_UP({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:loot-picked-up:lifetime")
    }),
    LOOT_PICKED_UP_WEEKLY({ mode ->
        StatisticID.fromCustom("arcade:pof:${mode?.id ?: "core"}:loot-picked-up:weekly", lifetime = StatisticLifetime.Weekly)
    }, StatisticLifetime.Weekly);

    fun toCore() = toStatisticID(null)
    fun toMode(mode: MiniGameModeMetadata?) = toStatisticID(mode)
}
