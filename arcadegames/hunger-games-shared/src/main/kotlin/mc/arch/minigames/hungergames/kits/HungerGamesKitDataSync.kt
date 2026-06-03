package mc.arch.minigames.hungergames.kits

import gg.scala.commons.persist.datasync.DataSyncKeys
import gg.scala.commons.persist.datasync.DataSyncService
import gg.scala.commons.persist.datasync.DataSyncSource
import gg.scala.flavor.service.Service
import java.util.logging.Level
import java.util.logging.Logger

/**
 * @author ArchMC
 */
@Service
object HungerGamesKitDataSync : DataSyncService<HungerGamesKitContainer>()
{
    private val logger = Logger.getLogger("HGKitDataSync")

    data object HGKitDataKeys : DataSyncKeys
    {
        override fun newStore() = "hungergames-kits"
        override fun store() = keyOf("hgkits", "store")
        override fun sync() = keyOf("hgkits", "sync")
    }

    override fun postReload()
    {
        val container = cached()

        try
        {
            val defaults = DefaultHungerGamesKits.buildDefaultKits()
            var changed = false

            // Add missing default kits
            val missing = defaults.filterKeys { it !in container.kits }
            if (missing.isNotEmpty())
            {
                logger.info("Found ${container.kits.size} existing kits, adding ${missing.size} missing default kits: ${missing.keys}")
                container.kits.putAll(missing)
                changed = true
            }

            // Sync prices from defaults for existing kits
            for ((kitId, defaultKit) in defaults)
            {
                val existingKit = container.kits[kitId] ?: continue

                for ((level, defaultLevel) in defaultKit.levels)
                {
                    val existingLevel = existingKit.levels[level] ?: continue

                    if (existingLevel.price != defaultLevel.price)
                    {
                        logger.info("Updating price for $kitId level $level: ${existingLevel.price} -> ${defaultLevel.price}")
                        existingLevel.price = defaultLevel.price
                        changed = true
                    }
                }
            }

            if (changed)
            {
                sync(container)
                logger.info("Successfully synced default HG kit data.")
            }
        } catch (e: Exception)
        {
            logger.log(Level.SEVERE, "Failed to load default HG kits", e)
        }
    }

    override fun locatedIn() = DataSyncSource.Mongo
    override fun keys() = HGKitDataKeys
    override fun type() = HungerGamesKitContainer::class.java
}
