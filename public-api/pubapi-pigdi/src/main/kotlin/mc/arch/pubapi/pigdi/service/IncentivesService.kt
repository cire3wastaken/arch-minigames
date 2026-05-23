package mc.arch.pubapi.pigdi.service

import mc.arch.pubapi.pigdi.entity.AkersProfileDocument
import mc.arch.pubapi.pigdi.repository.AkersProfileRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

@Service
class IncentivesService(
    private val akersProfileRepository: AkersProfileRepository,
    private val mongoTemplate: MongoTemplate
)
{
    @Value("\${incentives.ad-base-url:https://ads.arch.mc/ads}")
    private lateinit var adBaseUrl: String

    @Value("\${incentives.min-confirm-interval-ms:25000}")
    private var minConfirmIntervalMs: Long = 25_000L

    private val secureRandom = SecureRandom()

    companion object
    {
        private const val NONCE_LENGTH = 24
        private const val NONCE_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    }

    fun generateAdLink(uuid: UUID): GenerateResult
    {
        val (payload, hash) = newPayload(uuid)
        val url = "$adBaseUrl?payload=$payload"

        val query = Query(Criteria.where("_id").`is`(uuid.toString()))
        val update = Update()
            .set("currentAdNonceHash", hash)
            .setOnInsert("_id", uuid.toString())
            .setOnInsert("identifier", uuid.toString())
            .setOnInsert("adWatchCount", 0)
        mongoTemplate.upsert(query, update, AkersProfileDocument::class.java)

        return GenerateResult(url = url, payload = payload)
    }

    fun confirmAdWatch(payload: String): ConfirmOutcome
    {
        val decoded = decodePayload(payload)
            ?: return ConfirmOutcome.InvalidPayload

        val uuid = decoded.first
        val submittedHash = sha256(payload)

        val profile = akersProfileRepository.findById(uuid.toString()).orElse(null)
            ?: return ConfirmOutcome.ProfileNotFound

        val expectedHash = profile.currentAdNonceHash
            ?: return ConfirmOutcome.NonceInvalid

        if (expectedHash != submittedHash) return ConfirmOutcome.NonceInvalid

        val now = System.currentTimeMillis()
        val lastConfirmAt = profile.lastAdConfirmAt?.toLongOrNull()
        if (lastConfirmAt != null)
        {
            val nextAllowedAt = lastConfirmAt + minConfirmIntervalMs
            if (now < nextAllowedAt)
            {
                return ConfirmOutcome.TooEarly(
                    nextAllowedAt = nextAllowedAt,
                    retryAfterMs = nextAllowedAt - now
                )
            }
        }

        val (nextPayload, nextHash) = newPayload(uuid)
        val nextUrl = "$adBaseUrl?payload=$nextPayload"

        val query = Query(
            Criteria.where("_id").`is`(uuid.toString())
                .and("currentAdNonceHash").`is`(submittedHash)
        )
        val update = Update()
            .set("currentAdNonceHash", nextHash)
            .set("lastAdConfirmAt", now.toString())
            .inc("adWatchCount", 1)
        val updated = mongoTemplate.findAndModify(
            query,
            update,
            FindAndModifyOptions.options().returnNew(true),
            AkersProfileDocument::class.java
        ) ?: return ConfirmOutcome.NonceInvalid

        return ConfirmOutcome.Success(
            uuid = uuid.toString(),
            totalWatches = updated.adWatchCount,
            nextPayload = nextPayload,
            nextUrl = nextUrl,
            nextAllowedAt = now + minConfirmIntervalMs
        )
    }

    private fun newPayload(uuid: UUID): Pair<String, String>
    {
        val nonce = generateNonce()
        val raw = "$uuid:$nonce"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        return payload to sha256(payload)
    }

    private fun decodePayload(payload: String): Pair<UUID, String>?
    {
        return try
        {
            val decoded = String(Base64.getUrlDecoder().decode(payload))
            val parts = decoded.split(':', limit = 2)
            if (parts.size != 2) return null
            UUID.fromString(parts[0]) to parts[1]
        }
        catch (e: IllegalArgumentException)
        {
            null
        }
    }

    private fun generateNonce(): String
    {
        val sb = StringBuilder(NONCE_LENGTH)
        repeat(NONCE_LENGTH)
        {
            sb.append(NONCE_ALPHABET[secureRandom.nextInt(NONCE_ALPHABET.length)])
        }
        return sb.toString()
    }

    private fun sha256(input: String): String
    {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    data class GenerateResult(val url: String, val payload: String)

    sealed class ConfirmOutcome
    {
        data class Success(
            val uuid: String,
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
}
