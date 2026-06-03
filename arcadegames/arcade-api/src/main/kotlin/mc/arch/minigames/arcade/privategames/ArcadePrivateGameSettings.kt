package mc.arch.minigames.arcade.privategames

import mc.arch.minigames.arcade.ArcadeMode
import gg.tropic.practice.privategames.settings.PrivateGameSettingsRegistry
import gg.tropic.practice.privategames.settings.impl.IntSetting

object ArcadePrivateGameSettings
{
    const val GAME_TYPE = "arcade"

    @Volatile
    private var registered = false

    const val SUMO_ROUND_TIME = "sumo_round_time"
    const val SUMO_NEXT_ROUND_DELAY = "sumo_next_round_delay"
    const val OITC_KILL_TARGET = "oitc_kill_target"
    const val OITC_SPAWN_PROTECTION = "oitc_spawn_protection"
    const val OITC_RESPAWN_DELAY = "oitc_respawn_delay"
    const val RLGL_FINISH_TARGET = "rlgl_finish_target"
    const val RLGL_SPAWN_PROTECTION = "rlgl_spawn_protection"
    const val RLGL_GAME_TIMEOUT = "rlgl_game_timeout"

    const val DEFAULT_SUMO_ROUND_TIME = 60
    const val DEFAULT_SUMO_NEXT_ROUND_DELAY = 3
    const val DEFAULT_OITC_KILL_TARGET = 35
    const val DEFAULT_OITC_SPAWN_PROTECTION = 3
    const val DEFAULT_OITC_RESPAWN_DELAY = 3
    const val DEFAULT_RLGL_FINISH_TARGET = 3
    const val DEFAULT_RLGL_SPAWN_PROTECTION = 2
    const val DEFAULT_RLGL_GAME_TIMEOUT = 1

    fun register()
    {
        if (registered)
        {
            return
        }
        registered = true

        PrivateGameSettingsRegistry.register(ArcadeMode.SUMO.kitId) {
            IntSetting(
                id = SUMO_ROUND_TIME,
                displayName = "Sumo: Round Time",
                description = listOf(
                    "Seconds before a Sumo duel",
                    "times out with no winner."
                ),
                defaultValue = DEFAULT_SUMO_ROUND_TIME,
                min = 15,
                max = 180,
                step = 15
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.SUMO.kitId) {
            IntSetting(
                id = SUMO_NEXT_ROUND_DELAY,
                displayName = "Sumo: Next-Round Delay",
                description = listOf(
                    "Seconds between duels",
                    "in the bracket."
                ),
                defaultValue = DEFAULT_SUMO_NEXT_ROUND_DELAY,
                min = 1,
                max = 10,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.OITC.kitId) {
            IntSetting(
                id = OITC_KILL_TARGET,
                displayName = "OITC: Kills to Win",
                description = listOf(
                    "Kills required to win a",
                    "round of One in the Chamber."
                ),
                defaultValue = DEFAULT_OITC_KILL_TARGET,
                min = 5,
                max = 75,
                step = 5
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.OITC.kitId) {
            IntSetting(
                id = OITC_SPAWN_PROTECTION,
                displayName = "OITC: Spawn Protection",
                description = listOf(
                    "Seconds of invulnerability",
                    "after (re)spawning."
                ),
                defaultValue = DEFAULT_OITC_SPAWN_PROTECTION,
                min = 0,
                max = 10,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.OITC.kitId) {
            IntSetting(
                id = OITC_RESPAWN_DELAY,
                displayName = "OITC: Respawn Delay",
                description = listOf(
                    "Seconds before a killed",
                    "player respawns."
                ),
                defaultValue = DEFAULT_OITC_RESPAWN_DELAY,
                min = 0,
                max = 10,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.RED_LIGHT_GREEN_LIGHT.kitId) {
            IntSetting(
                id = RLGL_FINISH_TARGET,
                displayName = "RLGL: Finishers to End",
                description = listOf(
                    "How many players must reach",
                    "the finish before the game ends."
                ),
                defaultValue = DEFAULT_RLGL_FINISH_TARGET,
                min = 1,
                max = 10,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.RED_LIGHT_GREEN_LIGHT.kitId) {
            IntSetting(
                id = RLGL_SPAWN_PROTECTION,
                displayName = "RLGL: Spawn Protection",
                description = listOf(
                    "Seconds of invulnerability",
                    "after spawning."
                ),
                defaultValue = DEFAULT_RLGL_SPAWN_PROTECTION,
                min = 0,
                max = 10,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(ArcadeMode.RED_LIGHT_GREEN_LIGHT.kitId) {
            IntSetting(
                id = RLGL_GAME_TIMEOUT,
                displayName = "RLGL: Game Timeout",
                description = listOf(
                    "Minutes before the game",
                    "force-ends."
                ),
                defaultValue = DEFAULT_RLGL_GAME_TIMEOUT,
                min = 1,
                max = 10,
                step = 1
            )
        }
    }
}
