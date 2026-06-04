package mc.arch.minigames.arcade.broadcast

import gg.scala.lemon.handler.PlayerHandler
import gg.tropic.practice.expectation.ExpectationService
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import mc.arch.minigames.arcade.ArcadeMode
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

object ArcadeBroadcastTrigger
{
    const val PERMISSION = "arcade.broadcast"
    const val BYPASS_PERMISSION = "arcade.broadcast.bypass"

    fun giveItemIfPermitted(player: Player, slot: Int)
    {
        if (!player.hasPermission(PERMISSION))
        {
            return
        }

        // Private games are party-only, so there's nothing to broadcast.
        if (GameService.byPlayer(player)?.expectationModel?.isPrivateGame == true)
        {
            return
        }

        player.inventory.setItem(slot, ExpectationService.broadcastItem)
        player.updateInventory()
    }

    fun installInteractListener()
    {
        Events
            .subscribe(PlayerInteractEvent::class.java)
            .filter { !it.isCancelled }
            .filter {
                it.hasItem() &&
                    (it.action == Action.RIGHT_CLICK_BLOCK || it.action == Action.RIGHT_CLICK_AIR) &&
                    it.item.isSimilar(ExpectationService.broadcastItem)
            }
            .handler {
                it.isCancelled = true
                broadcast(it.player)
            }
    }

    fun broadcast(player: Player)
    {
        if (!player.hasPermission(PERMISSION))
        {
            player.sendMessage("${CC.RED}You don't have permission to broadcast.")
            return
        }

        val game = GameService.byPlayer(player)
            ?: return run {
                player.sendMessage("${CC.RED}You can only broadcast while waiting in an Arcade game.")
            }

        if (game.expectationModel.isPrivateGame)
        {
            player.sendMessage("${CC.RED}You can't broadcast a private game.")
            return
        }

        if (!(game.state(GameState.Waiting) || game.state(GameState.Starting)))
        {
            player.sendMessage("${CC.RED}You can only broadcast before the game starts.")
            return
        }

        val queueId = game.expectationModel.queueId
            ?: return run {
                player.sendMessage("${CC.RED}This game can't be broadcasted.")
            }

        val playerCooldownSeconds = if (player.hasPermission(BYPASS_PERMISSION))
        {
            ArcadeBroadcastPolicy.BYPASS_PLAYER_COOLDOWN_SECONDS
        } else
        {
            ArcadeBroadcastPolicy.DEFAULT_PLAYER_COOLDOWN_SECONDS
        }

        Schedulers.async().run {
            val gameDisplay = ArcadeBroadcastPolicy.displayNameFor(queueId)
                ?: ArcadeMode.entries.firstOrNull { it.queueId == queueId }?.displayName
                ?: return@run run {
                    player.sendMessage("${CC.RED}This isn't an Arcade game.")
                }

            val remainingSeconds = ArcadeBroadcastPolicy.remainingCooldownSeconds(player.uniqueId)
            if (remainingSeconds > 0L)
            {
                player.sendMessage("${CC.RED}You can broadcast again in ${formatCooldown(remainingSeconds)}.")
                return@run
            }

            val remainingQueueSeconds = ArcadeBroadcastPolicy.remainingQueueCooldownSeconds(queueId)
            if (remainingQueueSeconds > 0L)
            {
                player.sendMessage("${CC.RED}${gameDisplay} was just broadcasted. Try again in ${remainingQueueSeconds}s.")
                return@run
            }

            ArcadeBroadcastPolicy.startCooldown(player.uniqueId, playerCooldownSeconds)
            ArcadeBroadcastPolicy.startQueueCooldown(queueId)

            val broadcasterName = PlayerHandler.find(player.uniqueId)
                ?.getColoredName(prefixIncluded = true)
                ?: player.name

            ArcadeBroadcast.publish(
                broadcasterName = broadcasterName,
                gameDisplay = gameDisplay,
                queueId = queueId
            )

            player.sendMessage("${CC.GREEN}Broadcasted your ${gameDisplay} queue!")
        }
    }

    private fun formatCooldown(seconds: Long): String =
        if (seconds >= 60L) "${(seconds + 59L) / 60L}m" else "${seconds}s"
}
