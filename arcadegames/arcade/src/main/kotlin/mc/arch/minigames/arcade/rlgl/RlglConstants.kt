package mc.arch.minigames.arcade.rlgl

import java.time.Duration

object RlglConstants
{
    val GAME_TIMEOUT_MS = Duration.ofMinutes(1).toMillis()
    const val FREEZE_COUNTDOWN_SECONDS = 8
    val SPAWN_PROTECTION_MS = Duration.ofSeconds(2).toMillis()
    val GREEN_MIN_MS = Duration.ofMillis(2_500).toMillis()
    val GREEN_MAX_MS = Duration.ofSeconds(6).toMillis()
    val RED_MIN_MS = Duration.ofMillis(1_500).toMillis()
    val RED_MAX_MS = Duration.ofSeconds(4).toMillis()
    const val FINISH_TARGET = 3
    const val LOOT_SPAWN_INTERVAL_TICKS = 80L
    const val WINNER_COIN_REWARD = 450L
    const val RUNNERUP_COIN_REWARD = 250L
    const val THIRD_PLACE_COIN_REWARD = 150L
    const val HOST_COIN_REWARD = 150L
    const val PARTICIPATION_COIN_REWARD = 45L

    const val WINNER_XP_REWARD = 50L
    const val RUNNERUP_XP_REWARD = 30L
    const val THIRD_PLACE_XP_REWARD = 20L
    const val HOST_XP_REWARD = 15L
    const val PARTICIPATION_XP_REWARD = 15L
}
