package mc.arch.pubapi.pigdi.dto

data class GenerateAdLinkResponse(
    val url: String,
    val payload: String
)

data class AdConfirmResponse(
    val success: Boolean,
    val uuid: String,
    val name: String,
    val totalWatches: Int,
    val nextPayload: String,
    val nextUrl: String,
    val nextAllowedAt: Long
)

data class AdConfirmTooEarlyResponse(
    val error: String,
    val message: String,
    val nextAllowedAt: Long,
    val retryAfterMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)
