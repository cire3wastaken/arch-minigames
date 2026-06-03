package gg.tropic.practice.minigame

import gg.tropic.practice.profile.PracticeProfile
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player

interface MinigameCompetitiveCustomizer
{
    fun leaderboardsProvider(player: Player): Map<Int, Button>
    fun statisticsMenuProvider(profile: PracticeProfile): Map<Int, Button>

    fun holographicStatsProvider(player: Player): List<String>

    fun holographicTitleColor(): String = CC.B_WHITE
    fun holographicTickColor(): String = CC.PRI
    fun holographicFooterColor(): String = CC.WHITE
}
