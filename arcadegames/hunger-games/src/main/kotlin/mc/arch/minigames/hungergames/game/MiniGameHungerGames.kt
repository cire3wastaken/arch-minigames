package mc.arch.minigames.hungergames.game

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.core.plugin.*

/**
 * @author ArchMC
 */
@Plugin(
    name = "HungerGames",
    version = "%remote%/%branch%/%id%"
)
@PluginAuthorComposite(
    PluginAuthor("ArchMC")
)
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("Minigames")
)
class MiniGameHungerGames : ExtendedScalaPlugin()
