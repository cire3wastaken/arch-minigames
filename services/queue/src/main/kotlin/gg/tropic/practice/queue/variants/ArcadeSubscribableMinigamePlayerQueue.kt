package gg.tropic.practice.queue.variants

import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.manager.GameManager
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.queue.AbstractSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.QueueEntry
import gg.tropic.practice.queue.QueueType
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import mc.arch.minigames.arcade.ArcadeMode

/**
 * @author Subham
 * @since 6/15/25
 */
class ArcadeSubscribableMinigamePlayerQueue(
    kit: ImmutableKit,
    private val type: ArcadeMode
) : AbstractSubscribableMinigamePlayerQueue(
    miniGameMode = type,
    kit = kit,
    selectNewestInstance = true,
    queueType = QueueType.Casual, // No support for ranked yet
    miniProvider = type.providerVersion
)
{
    override val createsFallbackGameOnJoinFailure: Boolean = false

    override fun constructConfigurationForInitiatorEntry(entry: QueueEntry) =
        ArcadeMiniGameConfiguration(mode = type)

    @Volatile
    private var lastCreationInitiatedAt = 0L

    override fun onProcess(): List<QueueEntry>
    {
        val targetEntry = playersInQueue().firstOrNull()?.data
            ?: return emptyList()

        // A Completed game lingers in GameManager's listing until its server finishes
        // closeAndCleanup(). It must NOT count as the singleton instance — otherwise a
        // game that failed to start (e.g. not enough players during the countdown, which
        // flips it straight to Completed) would block every future join for this queueId.
        val existing = GameManager.allGames().firstOrNull {
            it.queueId == id && it.state != GameState.Completed
        }
        if (existing != null)
        {
            if (existing.state != GameState.Waiting && existing.state != GameState.Starting)
            {
                RedisShared.sendMessage(
                    targetEntry.players,
                    listOf("&cThis game is already in progress and cannot be joined right now.")
                )
                return listOf(targetEntry)
            }

            // Singleton invariant: a joinable game already exists. Don't let the
            // base class spawn a parallel one just because the host's instance has
            // been flagged failing — make the player wait instead.
            if (existing.server in AbstractSubscribableMinigamePlayerQueue.getFailingInstances())
            {
                RedisShared.sendMessage(
                    targetEntry.players,
                    listOf("&cThe ${type.name.lowercase()} game is unavailable. Please try again later.")
                )
                return listOf(targetEntry)
            }

            // Singleton invariant: when the game is at capacity, base.onProcess()
            // would filter the existing game out (it can't fit the joiner) and fall
            // through to spawning a parallel game. Reject instead.
            if (existing.players.size + targetEntry.players.size > type.maxPlayers())
            {
                RedisShared.sendMessage(
                    targetEntry.players,
                    listOf("&cThis ${type.name.lowercase()} game is full.")
                )
                return listOf(targetEntry)
            }

            return super.onProcess()
        }

        // No existing game yet. The just-created game can take several seconds to
        // show up in GameManager's cache; gate the create-new path so a second
        // joiner during that window doesn't spawn a parallel game.
        if (System.currentTimeMillis() - lastCreationInitiatedAt < CREATION_GUARD_MS)
        {
            RedisShared.sendMessage(
                targetEntry.players,
                listOf("&cA ${type.name.lowercase()} game is already being created. Please try again in a moment.")
            )
            return listOf(targetEntry)
        }

        lastCreationInitiatedAt = System.currentTimeMillis()
        return super.onProcess()
    }

    private companion object
    {
        const val CREATION_GUARD_MS = 30_000L
    }
}
