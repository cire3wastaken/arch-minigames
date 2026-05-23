package mc.arch.pubapi.akers.model

import gg.scala.commons.annotations.Model
import gg.scala.store.storage.storable.IDataStoreObject
import java.util.*

/**
 * AKERS profile for API key management and Discord linkage.
 * Plain data class with no framework dependencies.
 *
 * @author Subham
 * @since 12/27/24
 */
@Model
data class AkersProfile(
    val id: UUID,
    var discordId: String? = null,
    var discordUsername: String? = null,
    var linkedAt: Long? = null,
    var apiKeys: MutableList<AkersApiKey> = mutableListOf(),
    var banned: Boolean = false,
    var banReason: String? = null,
    var bannedBy: UUID? = null,
    var bannedAt: Long? = null,
    var currentAdNonceHash: String? = null,
    var lastAdConfirmAt: Long? = null,
    var adWatchCount: Int = 0,
    override val identifier: UUID = id
) : IDataStoreObject
{
    companion object
    {
        const val MAX_API_KEYS = 3
    }

    fun isLinked() = discordId != null

    fun canCreateKey() = apiKeys.count { !it.revoked } < MAX_API_KEYS

    fun getActiveKeys() = apiKeys.filter { !it.revoked }

    fun findKeyByToken(token: String) = apiKeys.find { it.token == token && !it.revoked }

    fun addKey(key: AkersApiKey): Boolean
    {
        if (!canCreateKey()) return false
        apiKeys.add(key)
        return true
    }

    fun revokeKey(token: String, revokedBy: UUID): Boolean
    {
        val key = apiKeys.find { it.token == token }
            ?: return false

        key.revoked = true
        key.revokedAt = System.currentTimeMillis()
        key.revokedBy = revokedBy
        return true
    }

    fun revokeAllKeys(revokedBy: UUID)
    {
        apiKeys.forEach { key ->
            if (!key.revoked)
            {
                key.revoked = true
                key.revokedAt = System.currentTimeMillis()
                key.revokedBy = revokedBy
            }
        }
    }
}
