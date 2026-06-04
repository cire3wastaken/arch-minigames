package mc.arch.minigames.arcade.lobby.extension

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import mc.arch.minigames.pof.PofGameType
import mc.arch.minigames.pof.services.formatCoreHolographicStatistics
import mc.arch.minigames.pof.services.formatStatistics

@Service
object PofArcadeExtension : AbstractArcadeGameExtension("pof")
{
    override val gameModes get() = PofGameType.gameModes.values

    @Configure
    fun configure()
    {
        ArcadeGameExtensionRegistry.register(this)
    }

    override fun formatStatistics(profile: PracticeProfile, mode: MiniGameModeMetadata?) =
        profile.formatStatistics(mode)

    override fun formatCoreHolographicStatistics(profile: PracticeProfile) =
        profile.formatCoreHolographicStatistics()
}
