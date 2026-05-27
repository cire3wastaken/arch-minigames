package mc.arch.minigames.versioned.generics

interface SlimeProvider
{
    fun queueGenerateWorld(worldGeneric: SlimeWorldGeneric<*>, newName: String)
    fun loadReadOnlyWorld(name: String): SlimeWorldGeneric<*>

    fun loadPersistentHostedWorld(name: String): SlimeWorldGeneric<*>
    fun createEmptyHostedWorld(name: String): SlimeWorldGeneric<*>

    fun saveWorld(generic: SlimeWorldGeneric<*>)

    fun importWorldFromBukkit(savedWorldFolder: java.io.File, newSlimeName: String)

    fun worldExists(name: String): Boolean

    fun listTemplates(): List<String>
    fun loadAndRegisterTemplate(name: String, readOnly: Boolean)

    /**
     * Persist a currently loaded+registered (writable) template's live block state back
     * to the slime store. A Bukkit `World.save()` alone only flushes chunks in-memory and
     * never writes through the slime loader. Returns false when nothing was persisted
     * (world not loaded, or loaded read-only).
     */
    fun saveLoadedTemplate(name: String): Boolean
    fun deleteTemplate(name: String)

    /** Slime format version byte (v9 = legacy SWM, v10+ = modern ASP). Caller-cached. */
    fun versionOf(name: String): Int?

    /**
     * Whether a template of the given slime format version must be loaded read-only on this
     * fleet. Each fleet only edits its own native format: the modern fleet refuses legacy
     * (v9) slimes so a writable load doesn't round-trip them into v13, and the legacy fleet
     * refuses modern (v10+) slimes (SWM can't parse them anyway). An unknown version (null)
     * is foreign and loaded read-only.
     */
    fun loadsReadOnly(formatVersion: Int?): Boolean
}
