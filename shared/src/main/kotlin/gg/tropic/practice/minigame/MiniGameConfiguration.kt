package gg.tropic.practice.minigame

import gg.tropic.practice.kit.feature.GameLifecycle
import net.evilblock.cubed.serializers.impl.AbstractTypeSerializable
import java.time.Duration

/**
 * @author GrowlyX
 * @since 8/23/2024
 */
interface MiniGameConfiguration : AbstractTypeSerializable
{
    val orchestratorID: String
    val lifecycleType: GameLifecycle
    val maximumPlayers: Int
    val maximumPlayersPerTeam: Int
    val minimumPlayersRequiredToEnterStarting: Int
    val minimumPlayersRequiredToFastForward: Int
    val startGameCountDown: Int
    val shouldBeAbleToReconnect: Boolean
    val reconnectThreshold: Long
    val gameDescription: String

    val preferFillingExistingTeams: Boolean
        get() = false
}
