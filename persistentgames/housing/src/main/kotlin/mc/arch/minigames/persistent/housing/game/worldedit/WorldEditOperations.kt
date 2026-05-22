package mc.arch.minigames.persistent.housing.game.worldedit

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import net.evilblock.cubed.util.bukkit.cuboid.Cuboid
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import kotlin.math.max
import kotlin.math.min

/**
 * Pure block-mutation routines for the in-house WorldEdit system.
 *
 * Each operation:
 *  - Resolves the target [World] from the session.
 *  - Returns a [WorldEditResult] describing what happened, never throwing.
 *  - Caller (the command layer) decides whether to display success/error text.
 */
object WorldEditOperations
{
    sealed class WorldEditResult
    {
        data class Success(val blocksAffected: Int) : WorldEditResult()
        data class Failure(val reason: String) : WorldEditResult()
    }

    private fun cuboidOf(world: World, a: Vector, b: Vector): Cuboid
    {
        val minX = min(a.blockX, b.blockX); val maxX = max(a.blockX, b.blockX)
        val minY = min(a.blockY, b.blockY); val maxY = max(a.blockY, b.blockY)
        val minZ = min(a.blockZ, b.blockZ); val maxZ = max(a.blockZ, b.blockZ)
        return Cuboid(
            Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble()),
            Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
        )
    }

    /**
     * Verifies the region is small enough (per-axis cap) and that every block
     * inside it is editable by [player] under the rules of [house].
     */
    private fun validateRegion(
        player: Player,
        house: PlayerHouse,
        region: Cuboid
    ): String?
    {
        val sx = region.upperX - region.lowerX + 1
        val sy = region.upperY - region.lowerY + 1
        val sz = region.upperZ - region.lowerZ + 1

        val cap = WorldEditService.MAX_AXIS
        if (sx > cap || sy > cap || sz > cap)
        {
            return "Region too large (${sx}x${sy}x${sz}). Per-axis cap is ${cap}."
        }

        val isAdmin = house.playerIsOrAboveAdministrator(player.uniqueId)
        val houseRegion = house.region

        if (!isAdmin && houseRegion != null)
        {
            val mayMutateOutside = house.allowsMutatingOutsideRegion == true
            if (!mayMutateOutside)
            {
                val corners = listOf(
                    Location(region.world, region.lowerX.toDouble(), region.lowerY.toDouble(), region.lowerZ.toDouble()),
                    Location(region.world, region.upperX.toDouble(), region.upperY.toDouble(), region.upperZ.toDouble())
                )
                if (corners.any { !houseRegion.contains(it) })
                {
                    return "Selection extends outside your realm region."
                }
            }
        }

        return null
    }

    @Suppress("DEPRECATION")
    private fun applyXMaterial(world: World, x: Int, y: Int, z: Int, xmat: XMaterial)
    {
        val mat = xmat.parseMaterial() ?: Material.STONE
        val block = world.getBlockAt(x, y, z)
        block.type = mat
        if (xmat.data > 0.toByte())
        {
            block.data = xmat.data
        }
    }

    @Suppress("DEPRECATION")
    private fun snapshot(world: World, x: Int, y: Int, z: Int): BlockSnapshot
    {
        val block = world.getBlockAt(x, y, z)
        return BlockSnapshot(block.type, block.data)
    }

    @Suppress("DEPRECATION")
    private fun restore(world: World, x: Int, y: Int, z: Int, snap: BlockSnapshot)
    {
        val block = world.getBlockAt(x, y, z)
        block.type = snap.material
        if (snap.data > 0.toByte())
        {
            block.data = snap.data
        }
    }

    fun parseMaterial(input: String): XMaterial? =
        XMaterial.matchXMaterial(input.uppercase()).orElse(null)

    // ------------------------------------------------------------------
    // //set
    // ------------------------------------------------------------------
    fun set(player: Player, house: PlayerHouse, material: XMaterial): WorldEditResult
    {
        val session = WorldEditService.sessionOf(player)
        if (!session.hasFullSelection())
        {
            return WorldEditResult.Failure("Set both pos1 and pos2 first (left/right-click with //wand).")
        }
        val world = player.server.getWorld(session.worldName!!)
            ?: return WorldEditResult.Failure("Selection world is no longer loaded.")
        if (world.name != player.world.name)
        {
            return WorldEditResult.Failure(
                "Your selection is in a different realm — reset pos1/pos2 here."
            )
        }
        val region = cuboidOf(world, session.pos1!!, session.pos2!!)

        validateRegion(player, house, region)?.let { return WorldEditResult.Failure(it) }

        var count = 0
        for (x in region.lowerX..region.upperX)
        {
            for (y in region.lowerY..region.upperY)
            {
                for (z in region.lowerZ..region.upperZ)
                {
                    applyXMaterial(world, x, y, z, material)
                    count++
                }
            }
        }
        return WorldEditResult.Success(count)
    }

    // ------------------------------------------------------------------
    // //cut  (copy into clipboard, then air out the source)
    // ------------------------------------------------------------------
    fun cut(player: Player, house: PlayerHouse): WorldEditResult
    {
        val copyResult = copy(player, house)
        if (copyResult is WorldEditResult.Failure) return copyResult

        return set(player, house, XMaterial.AIR)
    }

    // ------------------------------------------------------------------
    // //copy  (captures into session.clipboard, anchored at the player)
    // ------------------------------------------------------------------
    fun copy(player: Player, house: PlayerHouse): WorldEditResult
    {
        val session = WorldEditService.sessionOf(player)
        if (!session.hasFullSelection())
        {
            return WorldEditResult.Failure("Set both pos1 and pos2 first (left/right-click with //wand).")
        }
        val world = player.server.getWorld(session.worldName!!)
            ?: return WorldEditResult.Failure("Selection world is no longer loaded.")
        if (world.name != player.world.name)
        {
            return WorldEditResult.Failure(
                "Your selection is in a different realm — reset pos1/pos2 here."
            )
        }
        val region = cuboidOf(world, session.pos1!!, session.pos2!!)

        validateRegion(player, house, region)?.let { return WorldEditResult.Failure(it) }

        val sx = region.upperX - region.lowerX + 1
        val sy = region.upperY - region.lowerY + 1
        val sz = region.upperZ - region.lowerZ + 1

        val data = Array(sx) { x ->
            Array(sy) { y ->
                Array(sz) { z ->
                    snapshot(world, region.lowerX + x, region.lowerY + y, region.lowerZ + z)
                }
            }
        }

        // Offset = lowerCorner - playerBlock; paste reapplies at (player + offset).
        val origin = Vector(
            region.lowerX - player.location.blockX,
            region.lowerY - player.location.blockY,
            region.lowerZ - player.location.blockZ
        )

        session.clipboard = WorldEditClipboard(sx, sy, sz, origin, data)
        return WorldEditResult.Success(sx * sy * sz)
    }

    // ------------------------------------------------------------------
    // //paste  (drops the clipboard relative to the player)
    // ------------------------------------------------------------------
    fun paste(player: Player, house: PlayerHouse): WorldEditResult
    {
        val session = WorldEditService.sessionOf(player)
        val clipboard = session.clipboard
            ?: return WorldEditResult.Failure("Clipboard is empty. Run //copy or //cut first.")

        val world = player.world
        val baseX = player.location.blockX + clipboard.originOffset.blockX
        val baseY = player.location.blockY + clipboard.originOffset.blockY
        val baseZ = player.location.blockZ + clipboard.originOffset.blockZ

        val targetRegion = Cuboid(
            Location(world, baseX.toDouble(), baseY.toDouble(), baseZ.toDouble()),
            Location(
                world,
                (baseX + clipboard.sizeX - 1).toDouble(),
                (baseY + clipboard.sizeY - 1).toDouble(),
                (baseZ + clipboard.sizeZ - 1).toDouble()
            )
        )

        validateRegion(player, house, targetRegion)?.let { return WorldEditResult.Failure(it) }

        var count = 0
        for (x in 0 until clipboard.sizeX)
        {
            for (y in 0 until clipboard.sizeY)
            {
                for (z in 0 until clipboard.sizeZ)
                {
                    restore(world, baseX + x, baseY + y, baseZ + z, clipboard.blocks[x][y][z])
                    count++
                }
            }
        }
        return WorldEditResult.Success(count)
    }

    // ------------------------------------------------------------------
    // //sphere <material> <radius>  (centered at the player's block)
    // ------------------------------------------------------------------
    fun sphere(
        player: Player,
        house: PlayerHouse,
        material: XMaterial,
        radius: Int,
        hollow: Boolean
    ): WorldEditResult
    {
        if (radius <= 0)
        {
            return WorldEditResult.Failure("Radius must be > 0.")
        }
        val diameter = radius * 2 + 1
        if (diameter > WorldEditService.MAX_AXIS)
        {
            return WorldEditResult.Failure(
                "Sphere too large (diameter ${diameter}). Max diameter is ${WorldEditService.MAX_AXIS}."
            )
        }

        val world = player.world
        val cx = player.location.blockX
        val cy = player.location.blockY
        val cz = player.location.blockZ

        val region = Cuboid(
            Location(world, (cx - radius).toDouble(), (cy - radius).toDouble(), (cz - radius).toDouble()),
            Location(world, (cx + radius).toDouble(), (cy + radius).toDouble(), (cz + radius).toDouble())
        )
        validateRegion(player, house, region)?.let { return WorldEditResult.Failure(it) }

        val rSq = radius.toDouble() * radius
        val innerSq = (radius - 1).toDouble() * (radius - 1)

        var count = 0
        for (dx in -radius..radius)
        {
            for (dy in -radius..radius)
            {
                for (dz in -radius..radius)
                {
                    val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
                    if (distSq > rSq) continue
                    if (hollow && distSq < innerSq) continue

                    applyXMaterial(world, cx + dx, cy + dy, cz + dz, material)
                    count++
                }
            }
        }
        return WorldEditResult.Success(count)
    }
}
