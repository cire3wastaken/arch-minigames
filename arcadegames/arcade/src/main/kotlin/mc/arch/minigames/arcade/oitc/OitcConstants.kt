package mc.arch.minigames.arcade.oitc

import java.time.Duration

object OitcConstants
{
    const val KILL_TARGET = 35
    const val LONGRANGE_KILL_DISTANCE = 30.0
    val SPAWN_PROTECTION_MS = Duration.ofSeconds(3).toMillis()
    val GAME_TIMEOUT_MS = Duration.ofMinutes(8).toMillis()
    const val RESPAWN_DELAY_TICKS = 60L

    const val WINNER_COIN_REWARD = 450L
    const val HOST_COIN_REWARD = 150L
    const val PARTICIPATION_COIN_REWARD = 45L

    const val WINNER_XP_REWARD = 50L
    const val HOST_XP_REWARD = 15L
    const val PARTICIPATION_XP_REWARD = 15L
}
