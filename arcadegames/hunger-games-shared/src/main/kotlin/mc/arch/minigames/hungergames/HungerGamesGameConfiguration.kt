package mc.arch.minigames.hungergames

import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.minigame.MiniGameConfiguration

/**
 * @author ArchMC
 */
class HungerGamesGameConfiguration(
    val mode: HungerGamesMode,
    override val lifecycleType: GameLifecycle = GameLifecycle.SoulBound,
    override val orchestratorID: String = "hungergames",
    override val maximumPlayers: Int = mode.maxPlayers(),
    override val shouldBeAbleToReconnect: Boolean = false,
    override val reconnectThreshold: Long = 0L,
    override val maximumPlayersPerTeam: Int = mode.teamSize,
    override val gameDescription: String = "Survival Games ${mode.displayName}"
) : MiniGameConfiguration
{
    // Start the countdown as soon as we have the minimum viable game:
    // 2 players for solo (teamSize 1), 4 players for doubles (teamSize 2).
    override val minimumPlayersRequiredToEnterStarting = mode.teamSize * 2
    // Only fast-forward to the last 10s once the lobby is actually full.
    override val minimumPlayersRequiredToFastForward = mode.maxPlayers()
    // 3 minute countdown after hitting the minimum, hoping more players join.
    override val startGameCountDown = 180

    override fun getAbstractType() = HungerGamesGameConfiguration::class.java
}
