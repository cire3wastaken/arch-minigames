package gg.tropic.practice.commands

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.kit.findKitAcrossStores
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.queue.QueueCommunications
import gg.tropic.practice.queue.QueueIDParser
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.nms.MinecraftProtocol

@AutoRegister
object ArcadeJoinQueueCommand : ScalaCommand()
{
    private const val MIN_MODERN_PROTOCOL = 335 // 1.12

    @CommandAlias("arcadejoinqueue")
    fun onJoin(player: ScalaPlayer, queueId: String)
    {
        val mode = PracticeConfigurationService
            .minigameType().provide()
            .gameModes.values
            .firstOrNull { it.queueId == queueId }
            ?: return run {
                player.sendMessage("${CC.RED}That Arcade queue is no longer available.")
            }

        val bukkit = player.bukkit()

        if (mode.mode.providerVersion == MiniProviderVersion.MODERN)
        {
            val protocol = MinecraftProtocol.getPlayerVersion(bukkit)
            if (protocol in 1 until MIN_MODERN_PROTOCOL)
            {
                bukkit.sendMessage("${CC.RED}You cannot join this game on legacy Minecraft versions. Please use ${CC.B}1.12${CC.RED} or newer.")
                return
            }
        }

        val parsedQueueId = QueueIDParser.parseDetailed(mode.queueId)
        val kit = findKitAcrossStores(parsedQueueId.kitID)
            ?: return run {
                bukkit.sendMessage("${CC.RED}This mode is unavailable!")
            }

        QueueCommunications.joinQueue(
            kit = kit,
            queueType = parsedQueueId.queueType,
            teamSize = parsedQueueId.teamSize,
            player = bukkit
        )

        bukkit.sendMessage("${CC.GREEN}Joining a ${mode.displayName} game...")
    }
}
