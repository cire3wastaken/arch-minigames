package mc.arch.minigames.arcade.sumo

import java.time.Duration

object SumoConstants
{
    val ROUND_TIMEOUT_MS = Duration.ofMinutes(1).toMillis()
    const val NEXT_ROUND_DELAY_TICKS = 60L
    const val FIRST_MATCH_DELAY_TICKS = 60L
    const val WIN_CELEBRATION_DELAY_TICKS = 100L

    const val WINNER_COIN_REWARD = 450L
    const val HOST_COIN_REWARD = 150L
    const val PARTICIPATION_COIN_REWARD = 45L

    const val WINNER_XP_REWARD = 50L
    const val HOST_XP_REWARD = 15L
    const val PARTICIPATION_XP_REWARD = 15L
}
