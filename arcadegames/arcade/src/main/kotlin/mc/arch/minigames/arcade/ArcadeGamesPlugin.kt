package mc.arch.minigames.arcade

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.core.plugin.Plugin
import gg.scala.commons.core.plugin.PluginAuthor
import gg.scala.commons.core.plugin.PluginAuthorComposite
import gg.scala.commons.core.plugin.PluginDependency
import gg.scala.commons.core.plugin.PluginDependencyComposite
import gg.scala.commons.core.plugin.PluginWebsite
import gg.tropic.practice.minigame.MiniGameTypeProvider

@Plugin(
    name = "Arcade",
    version = "%remote%/%branch%/%id%"
)
@PluginAuthorComposite(
    PluginAuthor("GrowlyX")
)
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("Minigames"),
    PluginDependency("ASPaperPlugin", soft = true)
)
class ArcadeGamesPlugin : ExtendedScalaPlugin(), MiniGameTypeProvider
{
    override fun provide() = ArcadeTypeMetadata
}
