package mc.arch.minigames.arcade.broadcast

import gg.scala.commons.ScalaCommons
import gg.tropic.practice.suffixWhenDev
import java.util.UUID
import java.util.concurrent.TimeUnit

object ArcadeBroadcastPolicy
{
    val DEFAULT_PLAYER_COOLDOWN_SECONDS: Long = TimeUnit.MINUTES.toSeconds(5L)
    const val BYPASS_PLAYER_COOLDOWN_SECONDS = 30L

    const val QUEUE_COOLDOWN_SECONDS = 30L

    private val playerCooldownKeyPrefix by lazy { "arcade:broadcast:cooldown".suffixWhenDev() }
    private val queueCooldownKeyPrefix by lazy { "arcade:broadcast:queue-cooldown".suffixWhenDev() }
    private val catalogKey by lazy { "arcade:broadcast:catalog".suffixWhenDev() }

    private fun redis() = ScalaCommons.bundle().globals().redis().sync()

    private fun playerCooldownKey(player: UUID) = "$playerCooldownKeyPrefix:$player"
    private fun queueCooldownKey(queueId: String) = "$queueCooldownKeyPrefix:$queueId"

    fun remainingCooldownSeconds(player: UUID): Long =
        redis().ttl(playerCooldownKey(player)).coerceAtLeast(0L)

    fun startCooldown(player: UUID, seconds: Long)
    {
        redis().setex(playerCooldownKey(player), seconds, "1")
    }

    fun remainingQueueCooldownSeconds(queueId: String): Long =
        redis().ttl(queueCooldownKey(queueId)).coerceAtLeast(0L)

    fun startQueueCooldown(queueId: String)
    {
        redis().setex(queueCooldownKey(queueId), QUEUE_COOLDOWN_SECONDS, "1")
    }

    fun displayNameFor(queueId: String): String? = redis().hget(catalogKey, queueId)

    fun publishCatalog(displayNamesByQueueId: Map<String, String>)
    {
        val redis = redis()
        redis.del(catalogKey)
        if (displayNamesByQueueId.isNotEmpty())
        {
            redis.hset(catalogKey, displayNamesByQueueId)
        }
    }
}
