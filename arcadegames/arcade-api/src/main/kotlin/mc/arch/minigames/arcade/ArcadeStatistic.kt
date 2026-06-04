package mc.arch.minigames.arcade

import gg.tropic.practice.statistics.StatisticID

enum class ArcadeStatistic(val statisticID: StatisticID)
{
    SUMO_WINS(StatisticID.fromCustom(id = "arcade:sumo:wins")),
    SUMO_PARTICIPATED(StatisticID.fromCustom(id = "arcade:sumo:participated")),
    SUMO_KNOCKOFFS(StatisticID.fromCustom(id = "arcade:sumo:knockoffs")),
    SUMO_BEST_STREAK(StatisticID.fromCustom(id = "arcade:sumo:best_streak")),

    OITC_WINS(StatisticID.fromCustom(id = "arcade:oitc:wins")),
    OITC_PARTICIPATED(StatisticID.fromCustom(id = "arcade:oitc:participated")),
    OITC_KILLS(StatisticID.fromCustom(id = "arcade:oitc:kills")),
    OITC_BOW_KILLS(StatisticID.fromCustom(id = "arcade:oitc:bow_kills")),
    OITC_LONGRANGE_KILLS(StatisticID.fromCustom(id = "arcade:oitc:longrange_kills")),

    RLGL_WINS(StatisticID.fromCustom(id = "arcade:rlgl:wins")),
    RLGL_PARTICIPATED(StatisticID.fromCustom(id = "arcade:rlgl:participated")),
    RLGL_KILLS(StatisticID.fromCustom(id = "arcade:rlgl:kills")),
    RLGL_FINISHES(StatisticID.fromCustom(id = "arcade:rlgl:finishes")),
    RLGL_TIMES_CAUGHT(StatisticID.fromCustom(id = "arcade:rlgl:times_caught"));

    companion object
    {
        fun winOf(game: ArcadeMode) = arrayOf(
            participatedFor(game), winFor(game)
        )

        fun participationOf(game: ArcadeMode) = arrayOf(
            participatedFor(game)
        )

        private fun winFor(game: ArcadeMode) = when (game)
        {
            ArcadeMode.SUMO -> SUMO_WINS
            ArcadeMode.OITC -> OITC_WINS
            ArcadeMode.RED_LIGHT_GREEN_LIGHT -> RLGL_WINS
        }.statisticID

        private fun participatedFor(game: ArcadeMode) = when (game)
        {
            ArcadeMode.SUMO -> SUMO_PARTICIPATED
            ArcadeMode.OITC -> OITC_PARTICIPATED
            ArcadeMode.RED_LIGHT_GREEN_LIGHT -> RLGL_PARTICIPATED
        }.statisticID
    }
}
