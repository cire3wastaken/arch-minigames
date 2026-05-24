package mc.arch.minigame.pof

import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.minigame.MiniGameConfiguration
import java.time.Duration

class PofGameConfiguration(
    val mode: PofMode,
    val lootIntervalTicks: Long = 100L,
    val gameTimeoutMs: Long = Duration.ofMinutes(6).toMillis(),
    val rareUnlockMs: Long = Duration.ofSeconds(90).toMillis(),
    val freezeCountdownSeconds: Int = 10,
    val buildHeightAboveSpawn: Int = 8,
    val spawnProtectionMs: Long = Duration.ofSeconds(3).toMillis(),
    val deathmatchDamagePerTick: Double = 1.0,
    val deathmatchDamageIntervalTicks: Long = 20L,
    override val minimumPlayersRequiredToEnterStarting: Int =
        mode.minimumPlayersRequiredToEnterStarting,
    override val minimumPlayersRequiredToFastForward: Int =
        mode.minimumPlayersRequiredToEnterFastForward,
    override val startGameCountDown: Int = mode.startGameCountDown,
    override val lifecycleType: GameLifecycle = GameLifecycle.MiniGame,
    override val orchestratorID: String = "pof",
    override val maximumPlayers: Int = mode.maxPlayers(),
    override val maximumPlayersPerTeam: Int = if (mode == PofMode.SOLO) mode.maxPlayers() else mode.teamSize,
    override val preferFillingExistingTeams: Boolean = true,
    override val shouldBeAbleToReconnect: Boolean = false,
    override val reconnectThreshold: Long = 0L,
    override val gameDescription: String = "Pillar of Fortune ${mode.displayName}"
) : MiniGameConfiguration
{
    override fun getAbstractType() = PofGameConfiguration::class.java
}
