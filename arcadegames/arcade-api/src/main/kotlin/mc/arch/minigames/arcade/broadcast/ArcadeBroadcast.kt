package mc.arch.minigames.arcade.broadcast

import gg.scala.lemon.util.QuickAccess
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.FancyMessage
import net.md_5.bungee.api.chat.ClickEvent

object ArcadeBroadcast
{

    fun publish(broadcasterName: String, gameDisplay: String, queueId: String)
    {
        QuickAccess.sendGlobalFancyBroadcast(
            fancyMessage = render(broadcasterName, gameDisplay, queueId),
            permission = null
        )
    }

    fun render(broadcaster: String, game: String, queueId: String): FancyMessage = FancyMessage()
        .withMessage(" \n")
        .withMessage("${CC.BD_PURPLE}ARCADE GAME\n")
        .withMessage("${CC.BD_PURPLE}${broadcaster} ${CC.L_PURPLE}wants to play ${CC.D_PURPLE}${game} ${CC.L_PURPLE}with you!\n")
        .withMessage("\n${CC.BD_PURPLE}[CLICK TO JOIN]\n")
        .andHoverOf("${CC.D_PURPLE}Join the ${game} queue!")
        .andCommandOf(ClickEvent.Action.RUN_COMMAND, "/arcadejoinqueue $queueId")
        .withMessage(" ")
}
