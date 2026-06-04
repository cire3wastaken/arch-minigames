package gg.tropic.practice.map.metadata.sign

import gg.scala.commons.spatial.toPosition
import gg.tropic.practice.map.metadata.scanner.MetadataScannerUtilities
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.BlockFace
import java.util.LinkedList

/**
 * @author GrowlyX
 * @since 11/10/2022
 */
fun List<String>.parseIntoMetadata(location: Location, usedSynthetically: Boolean = false): MapSignMetadataModel?
{
    if (size < 2)
    {
        return null
    }

    val type = first()

    if (
        !type.startsWith("[") ||
        !type.endsWith("]")
    )
    {
        return null
    }

    val typeDelimited = type
        .removePrefix("[")
        .removeSuffix("]")

    val scanner = MetadataScannerUtilities
        .matches(typeDelimited)
        ?: return null

    val linked = LinkedList(this)
    linked.pop()

    return MapSignMetadataModel(
        metaType = typeDelimited,
        id = if (scanner.isAllExtra() && !usedSynthetically) "global" else linked.pop(),
        extraMetadata = linked.toList(),
        location = location.toPosition()
    )
}

val manualMappings = mutableMapOf(
    BlockFace.NORTH to 180.0F,
    BlockFace.WEST to 90.0F,
    BlockFace.SOUTH to 0.0F,
    BlockFace.EAST to -90.0F,

    BlockFace.SOUTH_WEST to 45.0F,
    BlockFace.SOUTH_EAST to -45.0F,
    BlockFace.NORTH_WEST to 135.0F,
    BlockFace.NORTH_EAST to -135.0F
)

val inverseValuesMappings = manualMappings.map { it.value to it.key }
    .associate { it.first to it.second }

fun List<MapSignMetadataModel>.normalize(world: World) = map { model ->
    val location = model.location.toLocation(world).clone()
    val block = location.block

    val facing = SignFacingResolver.facingOf(block)
    if (facing != null)
    {
        manualMappings[facing]?.let { location.yaw = it }
    }

    location.x += 0.500F
    location.z += 0.500F
    return@map location.toPosition()
}
