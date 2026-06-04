package mc.arch.minigames.pof

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.core.plugin.*
import gg.tropic.practice.minigame.MiniGameTypeProvider
import mc.arch.minigames.pof.PofGameType

@Plugin(
    name = "Pof",
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
class MiniGamePof : ExtendedScalaPlugin(), MiniGameTypeProvider
{
    override fun provide() = PofGameType
}
