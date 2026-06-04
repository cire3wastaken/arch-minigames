package mc.arch.minigames.hungergames

import gg.tropic.practice.minigame.MiniGameMode
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.queue.QueueType

/**
 * @author ArchMC
 */
enum class HungerGamesMode(
    val displayName: String,
    val queueType: QueueType,
    override val providerVersion: MiniProviderVersion
) : MiniGameMode
{
    SOLO_NORMAL("Solo Normal", QueueType.Casual, MiniProviderVersion.LEGACY)
    {
        override val teamSize = 1
        override val teamCount = 16

        override fun maxPlayers() = 16
    },
    DOUBLES_NORMAL("Doubles Normal", QueueType.Casual, MiniProviderVersion.LEGACY)
    {
        override val teamSize = 2
        override val teamCount = 12

        override fun maxPlayers() = 24
    }
}
