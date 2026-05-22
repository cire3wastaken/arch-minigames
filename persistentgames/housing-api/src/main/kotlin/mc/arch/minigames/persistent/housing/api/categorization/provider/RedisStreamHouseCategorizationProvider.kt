package mc.arch.minigames.persistent.housing.api.categorization.provider

import gg.tropic.practice.persistence.RedisShared
import mc.arch.minigames.persistent.housing.api.categorization.CategorizationConfig
import mc.arch.minigames.persistent.housing.api.categorization.HouseCategorizationProvider
import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationRequest
import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationResult
import net.evilblock.cubed.serializers.Serializers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Publishes requests onto a Redis stream (`housing:categorize:stage1`) and
 * polls a keyspace entry for the pipeline's final output. This is the
 * production transport - it matches the App Store pipeline design where each
 * stage is a separately-scaled worker and the transport between stages is a
 * durable, replayable log.
 *
 *  [request] -> XADD stage1 ---+
 *                              |
 *   stage1 worker -> XADD stage2
 *   stage2 worker -> XADD stage3
 *   stage3 worker -> SET result:<id>
 *                              |
 *   <--- poll for result -- ---+
 *
 * The Kotlin side owns exactly one of those steps: *publish*, then *poll*.
 * Everything in between can run and scale on dedicated nodes (GPU for
 * stage 1, CPU for stages 2–3, etc.).
 */
class RedisStreamHouseCategorizationProvider(
    private val config: CategorizationConfig = CategorizationConfig.DEFAULT,
    private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
) : HouseCategorizationProvider
{
    override val name: String = "redis-stream"

    override fun categorize(request: CategorizationRequest): CompletableFuture<CategorizationResult>
    {
        val sync = RedisShared.keyValueCache.sync()
        val payload = Serializers.gson.toJson(request)

        sync.xadd(
            config.redisRequestStream,
            mapOf(
                "request" to payload,
                "pipeline_version" to config.pipelineVersion,
                "submitted_at" to request.submittedAt.toString()
            )
        )

        val resultKey = "${config.redisResultKeyPrefix}${request.houseId}"
        val future = CompletableFuture<CategorizationResult>()
        val deadline = System.currentTimeMillis() + config.resultTimeoutMillis

        lateinit var poll: Runnable
        poll = Runnable {
            try
            {
                val raw = sync.get(resultKey)
                if (raw != null)
                {
                    val result = Serializers.gson.fromJson(raw, CategorizationResult::class.java)
                    if (result.inputContentHash == request.contentHash)
                    {
                        future.complete(result)
                        return@Runnable
                    }
                }

                if (System.currentTimeMillis() >= deadline)
                {
                    future.completeExceptionally(
                        IllegalStateException("categorization timed out after ${config.resultTimeoutMillis} ms for ${request.houseId}")
                    )
                    return@Runnable
                }

                executor.schedule(poll, config.resultPollIntervalMillis, TimeUnit.MILLISECONDS)
            }
            catch (t: Throwable)
            {
                future.completeExceptionally(t)
            }
        }

        executor.schedule(poll, config.resultPollIntervalMillis, TimeUnit.MILLISECONDS)
        return future
    }
}
