package mc.arch.minigame.pof.lobby

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.core.plugin.*

@Plugin(
    name = "PofLobby",
    version = "%remote%/%branch%/%id%"
)
@PluginAuthor("ArchMC")
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("Minigames")
)
class PofLobby : ExtendedScalaPlugin()
