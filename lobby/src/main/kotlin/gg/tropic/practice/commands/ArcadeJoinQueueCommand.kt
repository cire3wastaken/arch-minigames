package gg.tropic.practice.commands

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.minigame.joinGame
import net.evilblock.cubed.util.CC

@AutoRegister
object ArcadeJoinQueueCommand : ScalaCommand()
{
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

        mode.joinGame(player.bukkit())
    }
}
