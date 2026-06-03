package mc.arch.minigames.arcade.lobby

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.annotations.ServiceablePackage
import gg.scala.commons.core.plugin.*

@Plugin(
    name = "ArcadeLobby",
    version = "%remote%/%branch%/%id%"
)
@PluginAuthor("ArchMC")
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("Minigames"),
)
@ServiceablePackage("mc.arch")
class ArcadeLobby : ExtendedScalaPlugin()
