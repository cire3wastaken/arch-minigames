package gg.tropic.practice.queue.variants

import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.queue.AbstractSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.QueueEntry
import gg.tropic.practice.queue.QueueType
import mc.arch.minigame.pof.PofGameConfiguration
import mc.arch.minigame.pof.PofMode

class PofSubscribableMinigamePlayerQueue(
    kit: ImmutableKit,
    private val mode: PofMode
) : AbstractSubscribableMinigamePlayerQueue(
    miniGameMode = mode,
    kit = kit,
    selectNewestInstance = true,
    queueType = QueueType.Casual,
    miniProvider = MiniProviderVersion.MODERN
)
{
    override fun constructConfigurationForInitiatorEntry(entry: QueueEntry) =
        PofGameConfiguration(mode = mode)
}
