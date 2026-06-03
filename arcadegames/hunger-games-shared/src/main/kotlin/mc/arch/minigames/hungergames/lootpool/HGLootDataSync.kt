package mc.arch.minigames.hungergames.lootpool

import gg.scala.commons.persist.datasync.DataSyncKeys
import gg.scala.commons.persist.datasync.DataSyncService
import gg.scala.commons.persist.datasync.DataSyncSource
import gg.scala.flavor.service.Service

/**
 * @author ArchMC
 */
@Service
object HGLootDataSync : DataSyncService<HGLootContainer>()
{
    data object HGLootDataKeys : DataSyncKeys
    {
        override fun newStore() = "hungergames-loot-pools"
        override fun store() = keyOf("hgloot", "store")
        override fun sync() = keyOf("hgloot", "sync")
    }

    override fun locatedIn() = DataSyncSource.Mongo
    override fun keys() = HGLootDataKeys
    override fun type() = HGLootContainer::class.java
}
