package gg.tropic.practice.expectation

import gg.tropic.practice.games.duels.PvPConfiguration
import gg.tropic.practice.games.matchmaking.MatchmakingMetadata
import gg.tropic.practice.queue.QueueType
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.minigame.MiniGameConfiguration
import gg.tropic.practice.privategames.PrivateGameSettings
import gg.tropic.practice.provider.MiniProviderVersion
import java.util.*

/**
 * @author GrowlyX
 * @since 8/4/2022
 */
data class GameExpectation(
    val identifier: UUID,
    val players: MutableSet<UUID>,
    val teams: Set<GameTeam>,
    val kitId: String,
    val mapId: String,
    /**
     * Null queue types mean it is a private duel.
     */
    val queueType: QueueType? = null,
    val queueId: String? = null,
    val configuration: PvPConfiguration? = null,
    val matchmakingMetadataAPIV2: MatchmakingMetadata? = null,
    val miniGameConfiguration: MiniGameConfiguration? = null,
    val requiredMiniVersion: MiniProviderVersion = MiniProviderVersion.LEGACY,
    /**
     * Private games are party-exclusive games with no stat tracking
     * and optional game-specific settings.
     */
    val isPrivateGame: Boolean = false,
    val privateGameSettings: PrivateGameSettings? = null,
    val freeForAll: Boolean = false
)

