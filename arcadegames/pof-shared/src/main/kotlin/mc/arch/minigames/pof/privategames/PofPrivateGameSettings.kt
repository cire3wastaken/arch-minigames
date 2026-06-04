package mc.arch.minigames.pof.privategames

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.privategames.settings.PrivateGameSettingsRegistry
import gg.tropic.practice.privategames.settings.impl.IntSetting

@Service
object PofPrivateGameSettings
{
    const val GAME_TYPE = "pof"

    const val LOOT_DROP_INTERVAL = "loot_drop_interval"
    const val GAME_TIME_LIMIT = "game_time_limit"
    const val RARE_LOOT_UNLOCK = "rare_loot_unlock"

    const val DEFAULT_LOOT_DROP_INTERVAL = 5
    const val DEFAULT_GAME_TIME_LIMIT = 6
    const val DEFAULT_RARE_LOOT_UNLOCK = 90

    @Configure
    fun configure()
    {
        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            IntSetting(
                id = LOOT_DROP_INTERVAL,
                displayName = "Loot Drop Interval",
                description = listOf(
                    "Seconds between loot drops."
                ),
                defaultValue = DEFAULT_LOOT_DROP_INTERVAL,
                min = 1,
                max = 30,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            IntSetting(
                id = GAME_TIME_LIMIT,
                displayName = "Game Time Limit",
                description = listOf(
                    "Minutes until sudden death begins."
                ),
                defaultValue = DEFAULT_GAME_TIME_LIMIT,
                min = 1,
                max = 15,
                step = 1
            )
        }

        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            IntSetting(
                id = RARE_LOOT_UNLOCK,
                displayName = "Rare Loot Unlock",
                description = listOf(
                    "Seconds before rare items can drop."
                ),
                defaultValue = DEFAULT_RARE_LOOT_UNLOCK,
                min = 0,
                max = 300,
                step = 15
            )
        }
    }
}
