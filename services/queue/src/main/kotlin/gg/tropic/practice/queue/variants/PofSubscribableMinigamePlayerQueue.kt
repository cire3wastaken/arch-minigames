package gg.tropic.practice.queue.variants

import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.queue.AbstractSubscribableMinigamePlayerQueue
import gg.tropic.practice.queue.QueueEntry
import gg.tropic.practice.queue.QueueType
import mc.arch.minigames.pof.PofGameConfiguration
import mc.arch.minigames.pof.PofMode

class PofSubscribableMinigamePlayerQueue(
    kit: ImmutableKit,
    private val mode: PofMode
) : AbstractSubscribableMinigamePlayerQueue(
    miniGameMode = mode,
    kit = kit,
    selectNewestInstance = true,
    queueType = QueueType.Casual,
    miniProvider = mode.providerVersion
)
{
    override fun constructConfigurationForInitiatorEntry(entry: QueueEntry) =
        PofGameConfiguration(mode = mode)
}
