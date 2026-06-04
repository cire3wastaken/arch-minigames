package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import mc.arch.minigames.hungergames.HungerGamesTypeMetadata
import mc.arch.minigames.hungergames.kits.menu.MainHungerGamesKitMenu
import mc.arch.minigames.hungergames.statistics.formatCoreHolographicStatistics
import mc.arch.minigames.hungergames.statistics.formatStatistics
import org.bukkit.entity.Player

@Service
object HungerGamesArcadeExtension : AbstractArcadeGameExtension("hungergames")
{
    override val gameModes get() = HungerGamesTypeMetadata.gameModes.values

    @Configure
    fun configure()
    {
        ArcadeGameExtensionRegistry.register(this)
    }

    override fun manageButton(player: Player) = ArcadeManageButton(
        displayName = "Kit Shop",
        lore = listOf(
            "View and purchase kit",
            "levels for Survival Games."
        ),
        icon = XMaterial.CHEST,
        onClick = { clicker ->
            MainHungerGamesKitMenu().openMenu(clicker)
        }
    )

    override fun formatStatistics(profile: PracticeProfile, mode: MiniGameModeMetadata?) =
        profile.formatStatistics(mode)

    override fun formatCoreHolographicStatistics(profile: PracticeProfile) =
        profile.formatCoreHolographicStatistics()
}
