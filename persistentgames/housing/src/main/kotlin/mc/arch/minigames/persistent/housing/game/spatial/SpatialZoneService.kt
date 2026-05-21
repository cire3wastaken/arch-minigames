package mc.arch.minigames.persistent.housing.game.spatial

import gg.scala.commons.playerstatus.isVirtuallyInvisibleToSomeExtent
import gg.scala.commons.spatial.toLocation
import gg.tropic.practice.map.metadata.impl.MapZoneMetadata
import gg.tropic.practice.ugc.HostedWorldInstanceService
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.instance.HousingHostedWorldInstance
import mc.arch.minigames.persistent.housing.game.resources.getPlayerHouseFromInstance
import me.lucko.helper.Events
import net.evilblock.cubed.util.bukkit.Tasks
import net.evilblock.cubed.util.bukkit.cuboid.Cuboid
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent

object SpatialZoneService
{
    private var listenersRegistered = false

    fun applyWorldBorder(playerHouse: PlayerHouse, world: World)
    {
        val border = world.worldBorder

        border.setCenter(0.0, 0.0)
        border.setSize(playerHouse.plotSizeBlocks.toDouble(), 0)
        border.warningDistance = 5
    }

    private fun ensureListenersRegistered()
    {
        if (listenersRegistered) return
        listenersRegistered = true

        Events.subscribe(BlockBreakEvent::class.java)
            .handler { event ->
                val player = event.player
                val house = player.getPlayerHouseFromInstance()
                    ?: return@handler

                if (player.isVirtuallyInvisibleToSomeExtent())
                {
                    event.isCancelled = true
                    return@handler
                }

                val region = house.region
                    ?: return@handler

                if (house.playerIsOrAboveAdministrator(player.uniqueId))
                {
                    return@handler
                }

                if (!region.contains(event.block.location))
                {
                    if (house.allowsMutatingOutsideRegion != true)
                    {
                        event.isCancelled = true
                    }
                }
            }

        Events.subscribe(BlockPlaceEvent::class.java)
            .handler { event ->
                val player = event.player
                val house = player.getPlayerHouseFromInstance()
                    ?: return@handler

                if (player.isVirtuallyInvisibleToSomeExtent())
                {
                    event.isCancelled = true
                    return@handler
                }

                val region = house.region
                    ?: return@handler

                if (house.playerIsOrAboveAdministrator(player.uniqueId))
                {
                    return@handler
                }

                if (!region.contains(event.block.location))
                {
                    if (house.allowsMutatingOutsideRegion != true)
                    {
                        event.isCancelled = true
                    }
                }
            }

        Events.subscribe(EntityExplodeEvent::class.java)
            .handler { event ->
                if (event.entityType != EntityType.PRIMED_TNT) return@handler
                if (HostedWorldInstanceService.ofWorld(event.entity.world) !is HousingHostedWorldInstance) return@handler

                event.isCancelled = true
            }
    }

    fun configure(playerHouse: PlayerHouse, bounds: Int, mapZoneMetadata: MapZoneMetadata?, world: World)
    {
        ensureListenersRegistered()

        val zero = Location(world, 0.0, 100.0, 0.0)
        val regionAsCuboid = Cuboid(
            zero.clone().subtract(bounds.toDouble(), 100.0, bounds.toDouble()),
            zero.clone().add(bounds.toDouble(), 156.0, bounds.toDouble())
        )

        Tasks.sync {
            regionAsCuboid.getChunks().forEach { chunk ->
                val center = Location(
                    chunk.world,
                    (chunk.x * 16) + 7.5,
                    102.0,
                    (chunk.z * 16) + 7.5
                )

                val originalType = center.world
                    .getBlockAt(center)
                    .type

                center.world
                    .getBlockAt(center)
                    .setType(
                        Material.BEDROCK, true
                    )

                center.world
                    .getBlockAt(center)
                    .setType(
                        originalType, true
                    )
            }
        }

        applyWorldBorder(playerHouse, world)

        if (mapZoneMetadata != null)
        {
            playerHouse.region = Cuboid(
                mapZoneMetadata.bounds.lowerLeft.toLocation(world),
                mapZoneMetadata.bounds.upperRight.toLocation(world)
            )
            playerHouse.save()
        }
    }
}
