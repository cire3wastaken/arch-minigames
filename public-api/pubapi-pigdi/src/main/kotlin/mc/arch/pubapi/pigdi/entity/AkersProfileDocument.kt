package mc.arch.pubapi.pigdi.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.util.*

/**
 * MongoDB document for AKERS profiles.
 * Uses Spring Data MongoDB annotations.
 *
 * @author Subham
 * @since 12/27/24
 */
@Document(collection = "AkersProfile")
@CompoundIndexes(
    CompoundIndex(name = "apiKeys_token_idx", def = "{'apiKeys.token': 1}")
)
data class AkersProfileDocument(
    @Id
    val id: String, // UUID as string
    val identifier: String = id, // UUID as string
    var discordId: String? = null,
    var discordUsername: String? = null,
    var linkedAt: String? = null,
    var apiKeys: MutableList<ApiKeyDocument> = mutableListOf(),
    var banned: Boolean = false,
    var banReason: String? = null,
    var bannedBy: String? = null, // UUID as string
    var bannedAt: String? = null,
    var currentAdNonceHash: String? = null,
    var lastAdConfirmAt: String? = null,
    var adWatchCount: Int = 0
)
{
    companion object
    {
        const val MAX_API_KEYS = 3
    }

    fun isLinked() = discordId != null

    fun getActiveKeys() = apiKeys.filter { !it.revoked }

    fun findKeyByToken(token: String) = apiKeys.find { it.token == token && !it.revoked }
}

/**
 * Embedded document for API keys.
 */
data class ApiKeyDocument(
    val token: String,
    var name: String,
    val createdAt: String = System.currentTimeMillis().toString(),
    var lastUsedAt: String? = null,
    var revoked: Boolean = false,
    var revokedAt: String? = null,
    var revokedBy: String? = null // UUID as string
)
