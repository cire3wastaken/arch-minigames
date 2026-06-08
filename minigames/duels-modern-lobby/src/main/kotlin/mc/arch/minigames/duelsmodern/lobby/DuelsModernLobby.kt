package mc.arch.minigames.duelsmodern.lobby

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.annotations.container.ContainerEnable
import gg.scala.commons.core.plugin.*
import net.evilblock.cubed.serialize.ItemStackAdapter

/**
 * @author ArchMC
 */
@Plugin(
    name = "DuelsModern",
    version = "%remote%/%branch%/%id%"
)
@PluginAuthor("ArchMC")
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("Minigames"),
)
class DuelsModernLobby : ExtendedScalaPlugin()
{
    @ContainerEnable
    fun containerEnable()
    {
        ItemStackAdapter.writeModernToLegacy = true

        // fuck these skulls
        MojangProfileLookupLogFilter.install()
    }
}
