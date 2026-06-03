package mc.arch.minigames.arcade.lobby.extension

import gg.scala.flavor.service.Service

@Service
object ArcadeGameExtensionRegistry
{
    private val extensions = mutableMapOf<String, ArcadeGameExtension>()

    fun register(extension: ArcadeGameExtension)
    {
        extensions[extension.internalId] = extension
    }

    fun forId(internalId: String): ArcadeGameExtension? = extensions[internalId]

    fun all(): Collection<ArcadeGameExtension> = extensions.values
}
