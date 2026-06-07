package gg.tropic.practice.minigame.tasks

import com.cryptomorin.xseries.XSound
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.event.MiniGameEnterStartEvent
import gg.tropic.practice.minigame.event.MiniGameStartCancelEvent
import gg.tropic.practice.minigame.event.MiniGameStartTickEvent
import io.sentry.Sentry
import io.sentry.SentryLevel
import me.lucko.helper.Schedulers
import me.lucko.helper.scheduler.Task
import net.evilblock.cubed.util.CC
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 8/24/2024
 */
class MiniGamePreStartTask(
    private val game: AbstractMiniGameGameImpl<*>
) : Runnable, AutoCloseable
{
    companion object
    {
        private const val ABANDON_AFTER_NO_ONLINE_MS = 60_000L

        fun AbstractMiniGameGameImpl<*>.startPreStartTask() = with(MiniGamePreStartTask(this)) {
            task = Schedulers.sync()
                .runRepeating(
                    this,
                    0L, TimeUnit.SECONDS,
                    1L, TimeUnit.SECONDS
                )

            game.with(this)
            task.bindWith(game)
        }
    }

    lateinit var task: Task
    private var noOnlineSince: Long? = null

    override fun run()
    {
        val currentState = game.state
        if (!(currentState == GameState.Starting || currentState == GameState.Waiting))
        {
            task.closeAndReportException()
            return
        }

        val currentPlayers = game.expectationModel.players
        if (currentPlayers.isEmpty())
        {
            task.closeAndReportException()
            game.state = GameState.Completed
            game.closeAndCleanup()
            return
        }

        if (game.toBukkitPlayers().any { it != null })
        {
            noOnlineSince = null
        } else
        {
            val since = noOnlineSince ?: System.currentTimeMillis().also { noOnlineSince = it }
            if (System.currentTimeMillis() - since >= ABANDON_AFTER_NO_ONLINE_MS)
            {
                Sentry.captureMessage(
                    "Abandoning zombie minigame: no online players for ${ABANDON_AFTER_NO_ONLINE_MS / 1000}s while $currentState"
                ) { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setTag("alert_type", "zombie_game_abandoned")
                    scope.setExtra("game_state", currentState.name)
                    scope.setExtra("expected_players", currentPlayers.size.toString())
                    scope.setExtra("game_id", game.expectation.toString())
                }

                task.closeAndReportException()
                game.state = GameState.Completed
                game.closeAndCleanup()
                return
            }
        }

        if (currentState == GameState.Waiting)
        {
            if (game.fastTracked || currentPlayers.size >= game.miniGameLifecycle!!.configuration.minimumPlayersRequiredToEnterStarting)
            {
                game.state = GameState.Starting
                Bukkit.getPluginManager().callEvent(MiniGameEnterStartEvent(game))

                game.startCountDown = game.miniGameLifecycle!!.configuration.startGameCountDown
                run()
            }
            return
        }

        if (!game.fastTracked && currentPlayers.size < game.miniGameLifecycle!!.configuration.minimumPlayersRequiredToEnterStarting)
        {
            game.playSound(XSound.BLOCK_NOTE_BLOCK_HAT.parseSound()!!, 0.5f)
            game.sendMessage(
                "",
                "${CC.RED}Unable to meet minimum player requirements. Please try to queue again later.",
                ""
            )
            game.state = GameState.Waiting

            Bukkit.getPluginManager().callEvent(MiniGameStartCancelEvent(game))
            return
        }

        if (
            currentPlayers.size >= game.miniGameLifecycle!!.configuration.minimumPlayersRequiredToFastForward &&
            game.startCountDown > 10
        )
        {
            game.startCountDown = 10
        }

        if (game.startCountDown == 0)
        {
            val event = GameStartEvent(game)
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled)
            {
                game.state = GameState.Completed
                game.closeAndCleanup()
                return
            }

            task.closeAndReportException()

            game.state = GameState.Playing
            game.startTimestamp = System.currentTimeMillis()
            return
        }

        Bukkit.getPluginManager().callEvent(MiniGameStartTickEvent(game, game.startCountDown))
        game.startCountDown -= 1
    }

    override fun close()
    {

    }
}
