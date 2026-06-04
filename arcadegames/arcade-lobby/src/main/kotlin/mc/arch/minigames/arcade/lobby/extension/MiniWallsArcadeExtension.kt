package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.profile.PracticeProfile
import mc.arch.minigame.miniwalls.MiniWallsGameType
import mc.arch.minigame.miniwalls.loadout.editor.LoadoutEditMenu
import mc.arch.minigame.miniwalls.services.formatCoreHolographicStatistics
import mc.arch.minigame.miniwalls.services.formatStatistics
import mc.arch.minigames.arcade.lobby.menu.ArcadeManageMenu
import org.bukkit.entity.Player

@Service
object MiniWallsArcadeExtension : AbstractArcadeGameExtension("miniwalls")
{
    override val gameModes get() = MiniWallsGameType.gameModes.values

    @Configure
    fun configure()
    {
        ArcadeGameExtensionRegistry.register(this)
    }

    override fun manageButton(player: Player) = ArcadeManageButton(
        displayName = "Kit Editor",
        lore = listOf(
            "Edit your loadouts:",
            "- Soldier",
            "- Builder",
            "- Archer"
        ),
        icon = XMaterial.CHEST,
        onClick = { clicker ->
            LoadoutEditMenu(
                onReturn = { returning -> ArcadeManageMenu().openMenu(returning) }
            ).openMenu(clicker)
        }
    )

    override fun formatStatistics(profile: PracticeProfile, mode: MiniGameModeMetadata?) =
        profile.formatStatistics(mode)

    override fun formatCoreHolographicStatistics(profile: PracticeProfile) =
        profile.formatCoreHolographicStatistics()
}
