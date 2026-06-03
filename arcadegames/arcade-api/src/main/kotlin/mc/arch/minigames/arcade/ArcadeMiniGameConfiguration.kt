package mc.arch.minigames.arcade

import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.minigame.MiniGameConfiguration

/**
 * @author Subham
 * @since 7/26/25
 */
class ArcadeMiniGameConfiguration(
    val mode: ArcadeMode
) : MiniGameConfiguration
{
    override val orchestratorID: String = "arcade"
    override val lifecycleType: GameLifecycle = GameLifecycle.MiniGame
    override val maximumPlayers: Int = mode.maxPlayers()
    override val maximumPlayersPerTeam: Int = mode.teamSize
    override val minimumPlayersRequiredToEnterStarting: Int = mode.minimumPlayersRequiredToEnterStarting
    override val minimumPlayersRequiredToFastForward: Int = mode.minimumPlayersRequiredToEnterFastForward
    override val startGameCountDown: Int = mode.startGameCountDown
    override val shouldBeAbleToReconnect: Boolean = false
    override val reconnectThreshold: Long = 0L
    override val gameDescription: String = "Arcade"

    override fun getAbstractType() = ArcadeMiniGameConfiguration::class.java
}
