package gg.tropic.practice.kit

import gg.scala.commons.persist.datasync.DataSyncKeys
import gg.scala.commons.persist.datasync.DataSyncService
import gg.scala.commons.persist.datasync.DataSyncSource
import gg.scala.flavor.service.Service
import gg.tropic.practice.isModernKitFormat
import gg.tropic.practice.namespace
import gg.tropic.practice.namespaceShortened
import gg.tropic.practice.suffixWhenDev
import net.kyori.adventure.key.Key

/**
 * A read-only view of the *opposite* kit store from [KitService].
 *
 * Kits are stored per version: a 1.8 server's [KitService] reads the legacy store
 * (`mi-practice-kits`), a 1.21 server's reads the modern store
 * (`mi-practice-kits-modern`). That works when a fleet only serves one version,
 * but Arcade mixes legacy games (sumo/oitc, 1.8) and modern games (rlgl, 1.21)
 * under a single 1.21 lobby — so whichever store the lobby reads, the other
 * version's kits are invisible to it and queueing fails with "mode unavailable".
 *
 * This companion loads the other store so a server can resolve a kit that lives
 * in either one. Prefer [KitService] and fall back here via [findKitAcrossStores].
 * It only loads on Bukkit servers (where [KitService] also loads), so the full
 * [Kit]/[org.bukkit.inventory.ItemStack] deserialization is always available.
 */
@Service
object FallbackKitService : DataSyncService<KitContainer>()
{
    object FallbackKitKeys : DataSyncKeys
    {
        override fun newStore() = if (isModernKitFormat())
            "mi-practice-kits" else "mi-practice-kits-modern"

        override fun store() = Key.key(namespace(), "kits")
        override fun sync() = Key.key(
            namespaceShortened().suffixWhenDev(),
            if (isModernKitFormat()) "ksync" else "ksync-modern"
        )
    }

    override fun locatedIn() = DataSyncSource.Mongo

    override fun keys() = FallbackKitKeys
    override fun type() = KitContainer::class.java
}

/**
 * Resolve a kit by id from this server's primary [KitService] store, falling back
 * to the opposite store ([FallbackKitService]) for cross-version Arcade kits.
 *
 * Mirrors what the queue backend already does in `GameQueueManager.lookupKit`,
 * but for the Bukkit-side lookups (lobby queue pre-check, game resource build).
 */
fun findKitAcrossStores(kitId: String?): Kit?
{
    if (kitId == null) return null
    return KitService.cached().kits[kitId]
        ?: runCatching { FallbackKitService.cached().kits[kitId] }.getOrNull()
}
