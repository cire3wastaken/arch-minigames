package mc.arch.minigames.versioned.legacy

import com.grinderwolf.swm.api.SlimePlugin
import com.grinderwolf.swm.nms.v1_8_R3.CustomWorldServer
import com.grinderwolf.swm.plugin.config.WorldData
import mc.arch.minigames.versioned.generics.SlimeProvider
import mc.arch.minigames.versioned.generics.SlimeWorldGeneric
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld

/**
 * @author Subham
 * @since 8/5/25
 */
object LegacySlimeProvider : SlimeProvider
{
    private val slimePlugin = Bukkit.getServer().pluginManager
        .getPlugin("SlimeWorldManager") as SlimePlugin
    private val mongoLoader = slimePlugin.getLoader("mongodb")

    override fun queueGenerateWorld(worldGeneric: SlimeWorldGeneric<*>, newName: String)
    {
        val legacyWorld = worldGeneric as LegacySlimeWorld
        slimePlugin.generateWorld(
            legacyWorld.worldInstance.clone(newName)
        )
    }

    fun queueGenerateWorldUncloned(worldGeneric: SlimeWorldGeneric<*>)
    {
        val legacyWorld = worldGeneric as LegacySlimeWorld
        slimePlugin.generateWorld(
            legacyWorld.worldInstance
        )
    }


    override fun loadReadOnlyWorld(name: String): SlimeWorldGeneric<*>
    {
        val worldData = WorldData()
        worldData.isPvp = true
        worldData.difficulty = "normal"
        worldData.environment = "NORMAL"
        worldData.worldType = "DEFAULT"

        worldData.isAllowAnimals = false
        worldData.isAllowMonsters = false

        return LegacySlimeWorld(
            worldInstance = slimePlugin
                .loadWorld(
                    mongoLoader,
                    name,
                    true,
                    worldData.toPropertyMap()
                )
        )
    }

    override fun loadPersistentHostedWorld(name: String): SlimeWorldGeneric<*>
    {
        val worldData = WorldData()
        worldData.isPvp = true
        worldData.difficulty = "normal"
        worldData.environment = "NORMAL"
        worldData.worldType = "DEFAULT"

        worldData.isAllowAnimals = false
        worldData.isAllowMonsters = false

        return LegacySlimeWorld(
            worldInstance = slimePlugin
                .loadWorld(
                    LegacyGridFSContentProvider,
                    name,
                    false,
                    worldData.toPropertyMap()
                )
        )
    }

    override fun createEmptyHostedWorld(name: String): SlimeWorldGeneric<*>
    {
        val worldData = WorldData()
        worldData.isPvp = true
        worldData.difficulty = "normal"
        worldData.environment = "NORMAL"
        worldData.worldType = "DEFAULT"

        worldData.isAllowAnimals = false
        worldData.isAllowMonsters = false

        return LegacySlimeWorld(
            worldInstance = slimePlugin
                .createEmptyWorld(
                    LegacyGridFSContentProvider,
                    name,
                    false,
                    worldData.toPropertyMap()
                )
        )
    }

    override fun saveWorld(generic: SlimeWorldGeneric<*>)
    {
        val legacy = generic as LegacySlimeWorld
        val worldName = legacy.worldInstance.name
        val bukkitWorld = Bukkit.getWorld(worldName)

        if (bukkitWorld != null)
        {
            val craftWorld = bukkitWorld as CraftWorld

            if (craftWorld.getHandle() !is CustomWorldServer)
            {
                println("NOT A CUSTOM WORLD SERVER")
                return
            }

            val worldServer: CustomWorldServer = craftWorld.handle as CustomWorldServer

            Bukkit.unloadWorld(bukkitWorld, true)
            val serialized = worldServer.slimeWorld.serialize()

            println("Serialized ${serialized.size} bytes")

            LegacyGridFSContentProvider.saveWorld(
                worldName, serialized, false
            )
        }
    }

    override fun importWorldFromBukkit(savedWorldFolder: java.io.File, newSlimeName: String)
    {
        slimePlugin.importWorld(savedWorldFolder, newSlimeName, mongoLoader)
    }

    override fun loadsReadOnly(formatVersion: Int?): Boolean =
        // v9 is SWM's native format → writable here; v10+ is modern ASP and can't be parsed
        // by SWM anyway. Unknown version is treated as foreign.
        formatVersion == null || formatVersion >= 10

    override fun saveLoadedTemplate(name: String): Boolean
    {
        val bukkitWorld = Bukkit.getWorld(name) ?: return false
        val craftWorld = bukkitWorld as? CraftWorld ?: return false
        val worldServer = craftWorld.handle as? CustomWorldServer ?: return false

        val slimeWorld = worldServer.slimeWorld
        if (slimeWorld.isReadOnly) return false

        // The editor's World.save() flushes dirty NMS chunks back into the CraftSlimeWorld;
        // serialize() then captures that live block state. Write it straight back to the
        // mongo template store the world was loaded from (lock=false — we already hold it).
        return runCatching {
            mongoLoader.saveWorld(name, slimeWorld.serialize(), false)
        }.isSuccess
    }

    override fun worldExists(name: String) = mongoLoader.worldExists(name)

    override fun listTemplates(): List<String> = mongoLoader.listWorlds()

    override fun deleteTemplate(name: String)
    {
        if (mongoLoader.worldExists(name)) mongoLoader.deleteWorld(name)
    }

    override fun versionOf(name: String): Int? = runCatching {
        // Slime format: 2-byte magic (0xB1 0x0B) + 1-byte version. readOnly=true so
        // we don't acquire SWM's write lock just to peek at the header.
        val bytes = mongoLoader.loadWorld(name, true) ?: return null
        if (bytes.size < 3) null else bytes[2].toInt() and 0xFF
    }.getOrNull()

    override fun loadAndRegisterTemplate(name: String, readOnly: Boolean)
    {
        val worldData = WorldData()
        worldData.isPvp = true
        worldData.difficulty = "normal"
        worldData.environment = "NORMAL"
        worldData.worldType = "DEFAULT"

        worldData.isAllowAnimals = false
        worldData.isAllowMonsters = false

        val slimeWorld = slimePlugin.loadWorld(
            mongoLoader,
            name,
            readOnly,
            worldData.toPropertyMap()
        )

        slimePlugin.generateWorld(slimeWorld)
    }
}
