package mc.arch.minigames.hungergames.privategames

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.privategames.settings.PrivateGameSettingsRegistry
import gg.tropic.practice.privategames.settings.impl.BooleanSetting
import gg.tropic.practice.privategames.settings.impl.IntSetting

@Service
object HungerGamesPrivateGameSettings
{
    const val GAME_TYPE = "hungergames"

    // Setting IDs
    const val GRACE_PERIOD = "grace_period"
    const val CHEST_REFILL_TIMER = "chest_refill_timer"
    const val NATURAL_REGEN = "natural_regen"

    const val DEFAULT_GRACE_PERIOD = 30
    const val DEFAULT_CHEST_REFILL_TIMER = 0
    const val DEFAULT_NATURAL_REGEN = false

    @Configure
    fun configure()
    {
        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            IntSetting(
                id = GRACE_PERIOD,
                displayName = "Grace Period",
                description = listOf(
                    "Seconds of no-PvP at the",
                    "start of the game.",
                    "Set to 0 to disable."
                ),
                defaultValue = DEFAULT_GRACE_PERIOD,
                min = 0,
                max = 120,
                step = 10
            )
        }

        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            IntSetting(
                id = CHEST_REFILL_TIMER,
                displayName = "Chest Refill Timer",
                description = listOf(
                    "Seconds between chest refills.",
                    "Set to 0 for no refills."
                ),
                defaultValue = DEFAULT_CHEST_REFILL_TIMER,
                min = 0,
                max = 600,
                step = 30
            )
        }

        PrivateGameSettingsRegistry.register(GAME_TYPE) {
            BooleanSetting(
                id = NATURAL_REGEN,
                displayName = "Natural Regeneration",
                description = listOf(
                    "Allow players to regenerate",
                    "health from a full hunger bar."
                ),
                defaultValue = DEFAULT_NATURAL_REGEN
            )
        }
    }
}
