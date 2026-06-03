package mc.arch.minigames.arcade.broadcast

import gg.scala.aware.AwareBuilder
import gg.scala.aware.codec.codecs.interpretation.AwareMessageCodec
import gg.scala.aware.message.AwareMessage
import gg.scala.aware.thread.AwareThreadContext
import gg.tropic.practice.suffixWhenDev
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.FancyMessage
import net.md_5.bungee.api.chat.ClickEvent
import java.util.logging.Logger

object ArcadeBroadcast
{
    private val aware by lazy {
        AwareBuilder
            .of<AwareMessage>("arcade-queue-broadcast".suffixWhenDev())
            .codec(AwareMessageCodec)
            .logger(Logger.getAnonymousLogger())
            .build()
            .also { it.connect().toCompletableFuture().join() }
    }

    fun publish(broadcasterName: String, gameDisplay: String, queueId: String)
    {
        AwareMessage.of(
            "queue-open",
            aware,
            "broadcaster" to broadcasterName,
            "game" to gameDisplay,
            "queueId" to queueId,
        ).publish(AwareThreadContext.SYNC)
    }

    fun listen(handler: (broadcaster: String, game: String, queueId: String) -> Unit)
    {
        aware.listen("queue-open") {
            handler(
                retrieve<String>("broadcaster"),
                retrieve<String>("game"),
                retrieve<String>("queueId"),
            )
        }
    }


    fun render(broadcaster: String, game: String, queueId: String): FancyMessage = FancyMessage()
        .withMessage(" \n")
        .withMessage("${CC.BD_PURPLE}ARCADE QUEUE OPEN\n")
        .withMessage("${CC.BD_PURPLE}${broadcaster} ${CC.L_PURPLE}wants to play ${CC.D_PURPLE}${game} ${CC.L_PURPLE}with you!\n")
        .withMessage("\n${CC.BD_PURPLE}[CLICK TO JOIN]\n")
        .andHoverOf("${CC.D_PURPLE}Join the ${game} queue!")
        .andCommandOf(ClickEvent.Action.RUN_COMMAND, "/arcadejoinqueue $queueId")
        .withMessage(" ")
}
