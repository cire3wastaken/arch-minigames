package gg.solara.practice.editor.mapeditor

import me.lucko.helper.terminable.Terminable
import org.bukkit.Bukkit
import org.bukkit.World

/**
 * @author GrowlyX
 * @since 8/12/2024
 */
data class MapEditorInstance(
    val slimeWorldName: String,
    val world: World,
    val readOnly: Boolean
): Terminable
{
    override fun close()
    {
        if (Bukkit.getWorld(world.name) == null)
        {
            return
        }

        Bukkit.unloadWorld(world, false)
    }
}
