package mc.arch.minigames.persistent.housing.api.categorization

import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationRequest
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import java.security.MessageDigest

/**
 * Pulls every textual field off a [PlayerHouse] into a pipeline input.
 *
 * Strictly textual - no block composition, no schematic stats - because the
 * classifier is trained on natural language. The content hash is what the
 * cache keys off, so adding a new surface here invalidates stale categories
 * automatically on the next run.
 */
object HouseTextExtractor
{
    fun extract(house: PlayerHouse): CategorizationRequest
    {
        val displayName = house.displayName.orEmpty()
        val description = house.description.toList()
        val tags = house.tags.toList()

        val npcText = house.houseNPCMap.values.flatMap { npc ->
            buildList {
                add(npc.displayName)
                addAll(npc.aboveHeadText)
                addAll(npc.messagesToSend)
            }
        }

        val hologramText = house.houseHologramMap.values.flatMap { it.lines }

        val hash = contentHash(displayName, description, tags, npcText, hologramText)

        return CategorizationRequest(
            houseId = house.identifier,
            contentHash = hash,
            displayName = displayName,
            description = description,
            tags = tags,
            npcText = npcText,
            hologramText = hologramText
        )
    }

    private fun contentHash(vararg parts: Any): String
    {
        val digest = MessageDigest.getInstance("SHA-256")
        for (part in parts)
        {
            digest.update(part.toString().toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
