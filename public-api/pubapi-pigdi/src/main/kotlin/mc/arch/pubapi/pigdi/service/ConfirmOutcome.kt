package mc.arch.pubapi.pigdi.service

sealed class ConfirmOutcome
{
    data class Success(
        val uuid: String,
        val name: String,
        val totalWatches: Int,
        val nextPayload: String,
        val nextUrl: String,
        val nextAllowedAt: Long
    ) : ConfirmOutcome()

    object InvalidPayload : ConfirmOutcome()
    object ProfileNotFound : ConfirmOutcome()
    object NonceInvalid : ConfirmOutcome()
    data class TooEarly(val nextAllowedAt: Long, val retryAfterMs: Long) : ConfirmOutcome()
}
