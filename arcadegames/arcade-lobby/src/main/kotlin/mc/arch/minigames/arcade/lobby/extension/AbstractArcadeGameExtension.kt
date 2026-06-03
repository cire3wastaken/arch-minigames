package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.MinigameCompetitiveCustomizer
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.profile.PracticeProfileService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

abstract class AbstractArcadeGameExtension(
    override val internalId: String,
) : ArcadeGameExtension, MinigameCompetitiveCustomizer
{
    protected abstract val gameModes: Collection<MiniGameModeMetadata>

    protected abstract fun formatStatistics(profile: PracticeProfile, mode: MiniGameModeMetadata?): List<String>

    protected abstract fun formatCoreHolographicStatistics(profile: PracticeProfile): List<String>

    override fun competitive() = this

    override fun leaderboardsProvider(player: Player) = mapOf<Int, Button>()

    override fun statisticsMenuProvider(profile: PracticeProfile) = mapOf(
        13 to ItemBuilder.of(XMaterial.ENDER_EYE)
            .name("${CC.RED}Core Statistics")
            .setLore(formatStatistics(profile, null))
            .toButton()
    ) + gameModes.mapIndexed { index, metadata ->
        (28 + (2 * index)) to metadata.toRawItem()
            .name("${CC.GREEN}${metadata.displayName} Statistics")
            .setLore(formatStatistics(profile, metadata))
            .toButton()
    }

    override fun holographicStatsProvider(player: Player): List<String>
    {
        val profile = PracticeProfileService.find(player)
            ?: return listOf("${CC.GRAY}???")

        return formatCoreHolographicStatistics(profile)
    }
}
