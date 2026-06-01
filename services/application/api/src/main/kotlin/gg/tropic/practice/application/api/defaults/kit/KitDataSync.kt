package gg.tropic.practice.application.api.defaults.kit

import gg.scala.commons.persist.datasync.DataSyncKeys
import gg.scala.commons.persist.datasync.DataSyncService
import gg.scala.commons.persist.datasync.DataSyncSource
import gg.tropic.practice.namespace
import gg.tropic.practice.namespaceShortened
import gg.tropic.practice.suffixWhenDev
import net.kyori.adventure.key.Key

/**
 * @author GrowlyX
 * @since 9/24/2023
 */
object KitDataSync : DataSyncService<ImmutableKitContainer>()
{
    object DPSMapKeys : DataSyncKeys
    {
        override fun newStore() = "mi-practice-kits"

        override fun store() = Key.key(namespace(), "kits")
        override fun sync() = Key.key(namespaceShortened().suffixWhenDev(), "ksync")
    }

    override fun locatedIn() = DataSyncSource.Mongo

    override fun keys() = DPSMapKeys
    override fun type() = ImmutableKitContainer::class.java

    object Modern : DataSyncService<ImmutableKitContainer>()
    {
        object DPSMapKeys : DataSyncKeys
        {
            override fun newStore() = "mi-practice-kits-modern"

            override fun store() = Key.key(namespace(), "kits-modern")
            override fun sync() = Key.key(namespaceShortened().suffixWhenDev(), "ksync-modern")
        }

        override fun locatedIn() = DataSyncSource.Mongo

        override fun keys() = DPSMapKeys
        override fun type() = ImmutableKitContainer::class.java
    }
}
