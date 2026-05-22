package mc.arch.minigames.persistent.housing.api.categorization

import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationRequest
import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationResult
import java.util.concurrent.CompletableFuture

/**
 * Contract between the JVM-side housing plugin and the ML pipeline. Two
 * impls ship: an HTTP one (blocking gateway call) and a Redis-streams one
 * (fully distributed - request published to `housing:categorize:stage1`,
 * result read back from `housing:categorize:result:<houseId>`).
 *
 * Everything asynchronous - categorization is measured in hundreds of ms at
 * best (with a 1B Gemma), and callers should never pin a tick thread on it.
 */
interface HouseCategorizationProvider
{
    fun categorize(request: CategorizationRequest): CompletableFuture<CategorizationResult>

    val name: String
}
