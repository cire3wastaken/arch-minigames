package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import mc.arch.minigames.skywars.SkyWarsTypeMetadata
import mc.arch.minigames.skywars.menus.SelectKitMenu
import mc.arch.minigames.skywars.statistics.formatCoreHolographicStatistics
import mc.arch.minigames.skywars.statistics.formatStatistics
import org.bukkit.entity.Player

@Service
object SkyWarsArcadeExtension : AbstractArcadeGameExtension("skywars")
{
    override val gameModes get() = SkyWarsTypeMetadata.gameModes.values

    @Configure
    fun configure()
    {
        ArcadeGameExtensionRegistry.register(this)
    }

    override fun manageButton(player: Player) = ArcadeManageButton(
        displayName = "Kits",
        lore = listOf(
            "Purchase kits for the Mini",
            "mode. Select them in-game."
        ),
        icon = XMaterial.CHEST,
        onClick = { clicker ->
            SelectKitMenu(allowSelect = false).openMenu(clicker)
        }
    )

    override fun formatStatistics(profile: PracticeProfile, mode: MiniGameModeMetadata?) =
        profile.formatStatistics(mode)

    override fun formatCoreHolographicStatistics(profile: PracticeProfile) =
        profile.formatCoreHolographicStatistics()
}
