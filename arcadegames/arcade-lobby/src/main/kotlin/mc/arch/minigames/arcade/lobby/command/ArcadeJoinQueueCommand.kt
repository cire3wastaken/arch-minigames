package mc.arch.minigames.arcade.lobby.command

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import gg.tropic.practice.minigame.joinGame
import mc.arch.minigames.arcade.ArcadeTypeMetadata
import net.evilblock.cubed.util.CC

/**
 * Backs the "[CLICK TO JOIN]" action on an Arcade queue broadcast. Resolves the raw
 * expectation-model queueId (e.g. "mw_main:Casual:1v1") to its mode and joins through the
 * same [joinGame] path the Arcade selector menu uses — a plain /joinqueue does not
 * understand this queueId format.
 */
@AutoRegister
object ArcadeJoinQueueCommand : ScalaCommand()
{
    @CommandAlias("arcadejoinqueue")
    fun onJoin(player: ScalaPlayer, queueId: String)
    {
        val mode = ArcadeTypeMetadata.gameModes.values
            .firstOrNull { it.queueId == queueId }
            ?: return run {
                player.sendMessage("${CC.RED}That Arcade queue is no longer available.")
            }

        mode.joinGame(player.bukkit())
    }
}
