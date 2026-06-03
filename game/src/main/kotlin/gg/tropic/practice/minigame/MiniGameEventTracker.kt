package gg.tropic.practice.minigame

import gg.tropic.practice.games.event.GameStartEvent
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.scheduler.Task
import java.time.Duration
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author Subham
 * @since 5/26/25
 */
data class MiniGameEventTracker(private val lifecycle: MiniGameLifecycle<*>) : Runnable
{
    private var task: Task? = null
    private val events = LinkedList(lifecycle.events)
    private var lastEventEnd = System.currentTimeMillis()
    private var nextEvent: MiniGameEvent? = null
    fun subscribe()
    {
        Events
            .subscribe(GameStartEvent::class.java)
            .filter { it.game.identifier == lifecycle.game.identifier }
            .handler {
                lastEventEnd = System.currentTimeMillis()
                if (events.isNotEmpty())
                {
                    nextEvent = events.pop()
                    task = Schedulers.sync()
                        .runRepeating(this, 0L, 20L)
                        .apply { bindWith(lifecycle.game) }
                }
            }
            .bindWith(lifecycle.game)
    }

    fun nextEvent() = nextEvent
    fun timeUntilNextEvent() = (((nextEvent?.duration?.toMillis() ?: Duration.ofMinutes(10L).toMillis()) + lastEventEnd) - System.currentTimeMillis())
        .coerceAtLeast(0)

    override fun run()
    {
        val current = nextEvent
        if (current == null)
        {
            task?.closeAndReportException()
            return
        }

        if (timeUntilNextEvent() > 0)
        {
            return
        }

        try
        {
            current.execute()
        } catch (exception: Exception)
        {
            Logger.getLogger("MiniGameEventTracker")
                .log(Level.SEVERE, "Failed to execute minigame event '${current.description}'", exception)
        } finally
        {
            lastEventEnd = System.currentTimeMillis()
            nextEvent = if (events.isNotEmpty()) events.pop() else null
        }
    }
}
