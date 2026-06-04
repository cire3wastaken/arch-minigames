package mc.arch.minigames.arcade

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger

@AutoRegister
@CommandPermission(ArcadeBroadcastTrigger.PERMISSION)
object BroadcastQueueCommand : ScalaCommand()
{
    @CommandAlias("broadcastqueue|bq")
    fun onBroadcastQueue(player: ScalaPlayer)
    {
        ArcadeBroadcastTrigger.broadcast(player.bukkit())
    }
}
