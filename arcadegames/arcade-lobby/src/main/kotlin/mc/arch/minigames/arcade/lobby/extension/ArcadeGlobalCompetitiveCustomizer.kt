package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.tropic.game.extensions.profile.CorePlayerProfileService
import gg.tropic.practice.extensions.toShortString
import gg.tropic.practice.minigame.MinigameCompetitiveCustomizer
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.statistics.StatisticID
import gg.tropic.practice.statistics.numericalValueOf
import mc.arch.minigame.miniwalls.services.CoreMiniWallsStatistic
import mc.arch.minigames.pof.services.CorePofStatistic
import mc.arch.minigames.arcade.ArcadeMode
import mc.arch.minigames.arcade.ArcadeStatistic
import mc.arch.minigames.hungergames.statistics.CoreHungerGamesStatistic
import mc.arch.minigames.skywars.statistics.CoreSkyWarsStatistic
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player

object ArcadeGlobalCompetitiveCustomizer : MinigameCompetitiveCustomizer
{
    private const val ARCADE_LEVEL_ID = "arcade"

    override fun holographicTitleColor(): String = CC.BD_PURPLE
    override fun holographicTickColor(): String = CC.L_PURPLE
    override fun holographicFooterColor(): String = CC.L_PURPLE

    private data class StatLine(val label: String, val id: StatisticID)

    private data class GameStats(
        val displayName: String,
        val icon: XMaterial,
        val wins: StatisticID,
        val kills: StatisticID?,
        val plays: StatisticID,
        val losses: StatisticID? = null,
        val extra: List<StatLine> = emptyList(),
    )

    private val games = listOf(
        GameStats(
            "SkyWars", XMaterial.FEATHER,
            CoreSkyWarsStatistic.WINS.toCore(),
            CoreSkyWarsStatistic.KILLS.toCore(),
            CoreSkyWarsStatistic.PLAYS.toCore(),
            CoreSkyWarsStatistic.LOSSES.toCore(),
            extra = listOf(
                StatLine("Bow Kills", CoreSkyWarsStatistic.BOW_KILLS.toCore()),
                StatLine("Void Kills", CoreSkyWarsStatistic.VOID_KILLS.toCore()),
                StatLine("Chests Opened", CoreSkyWarsStatistic.CHESTS_OPENED.toCore()),
                StatLine("Arrows Shot", CoreSkyWarsStatistic.ARROWS_SHOT.toCore()),
            )
        ),
        GameStats(
            "Mini Walls", XMaterial.COBBLESTONE,
            CoreMiniWallsStatistic.WINS.toCore(),
            CoreMiniWallsStatistic.KILLS.toCore(),
            CoreMiniWallsStatistic.PLAYS.toCore(),
            CoreMiniWallsStatistic.LOSSES.toCore(),
            extra = listOf(
                StatLine("Final Kills", CoreMiniWallsStatistic.FINAL_KILLS.toCore()),
                StatLine("Withers Killed", CoreMiniWallsStatistic.WITHERS_KILLED.toCore()),
                StatLine("Win Streak", CoreMiniWallsStatistic.WIN_STREAK.toCore()),
            )
        ),
        GameStats(
            "Hunger Games", XMaterial.IRON_SWORD,
            CoreHungerGamesStatistic.WINS.toCore(),
            CoreHungerGamesStatistic.KILLS.toCore(),
            CoreHungerGamesStatistic.PLAYS.toCore(),
            CoreHungerGamesStatistic.LOSSES.toCore(),
            extra = listOf(
                StatLine("Assists", CoreHungerGamesStatistic.ASSISTS.toCore()),
                StatLine("Win Streak", CoreHungerGamesStatistic.WIN_STREAK.toCore()),
            )
        ),
        GameStats(
            "Sumo", ArcadeMode.SUMO.icon,
            ArcadeStatistic.SUMO_WINS.statisticID,
            null,
            ArcadeStatistic.SUMO_PARTICIPATED.statisticID,
            extra = listOf(
                StatLine("Knockoffs", ArcadeStatistic.SUMO_KNOCKOFFS.statisticID),
                StatLine("Best Win Streak", ArcadeStatistic.SUMO_BEST_STREAK.statisticID),
            )
        ),
        GameStats(
            "One in the Chamber", ArcadeMode.OITC.icon,
            ArcadeStatistic.OITC_WINS.statisticID,
            ArcadeStatistic.OITC_KILLS.statisticID,
            ArcadeStatistic.OITC_PARTICIPATED.statisticID,
            extra = listOf(
                StatLine("Bow Kills", ArcadeStatistic.OITC_BOW_KILLS.statisticID),
                StatLine("Long-Range Kills", ArcadeStatistic.OITC_LONGRANGE_KILLS.statisticID),
            )
        ),
        GameStats(
            "Red Light, Green Light", ArcadeMode.RED_LIGHT_GREEN_LIGHT.icon,
            ArcadeStatistic.RLGL_WINS.statisticID,
            ArcadeStatistic.RLGL_KILLS.statisticID,
            ArcadeStatistic.RLGL_PARTICIPATED.statisticID,
            extra = listOf(
                StatLine("Finishes", ArcadeStatistic.RLGL_FINISHES.statisticID),
                StatLine("Times Caught", ArcadeStatistic.RLGL_TIMES_CAUGHT.statisticID),
            )
        ),
        GameStats(
            "Pillar of Fortune", XMaterial.GOLD_BLOCK,
            CorePofStatistic.WINS.toCore(),
            CorePofStatistic.KILLS.toCore(),
            CorePofStatistic.PLAYS.toCore(),
            CorePofStatistic.LOSSES.toCore(),
            extra = listOf(
                StatLine("Win Streak", CorePofStatistic.WIN_STREAK.toCore()),
                StatLine("Blocks Placed", CorePofStatistic.BLOCKS_PLACED.toCore()),
                StatLine("Loot Picked Up", CorePofStatistic.LOOT_PICKED_UP.toCore()),
            )
        ),
    )

    private val cardSlots = listOf(20, 22, 24, 29, 31, 33, 40)

    private fun PracticeProfile.total(ids: List<StatisticID?>) =
        ids.filterNotNull().sumOf { numericalValueOf(it)?.score?.toLong() ?: 0L }

    private fun PracticeProfile.totalLosses() = games.sumOf { game ->
        game.losses?.let { total(listOf(it)) }
            ?: (total(listOf(game.plays)) - total(listOf(game.wins))).coerceAtLeast(0L)
    }

    override fun holographicStatsProvider(player: Player): List<String>
    {
        val profile = PracticeProfileService.find(player)
            ?: return listOf("${CC.GRAY}???")

        val lines = mutableListOf<String>()

        CorePlayerProfileService.find(profile.identifier)?.let { coreProfile ->
            val level = coreProfile.getLevelInfo(ARCADE_LEVEL_ID)
            lines += "${CC.GRAY}Level: ${CC.GRAY}${level.formattedDisplay}"
            lines += "${CC.GRAY}Progress: ${CC.GREEN}${
                level.currentXP.toLong().toShortString()
            }${CC.GRAY}/${CC.AQUA}${
                level.xpRequiredForNext.toLong().toShortString()
            }"
            lines += ""
        }

        lines += "${CC.GRAY}Wins: ${CC.WHITE}${Numbers.format(profile.total(games.map { it.wins }))}"
        lines += "${CC.GRAY}Losses: ${CC.WHITE}${Numbers.format(profile.totalLosses())}"
        lines += "${CC.GRAY}Games Played: ${CC.WHITE}${Numbers.format(profile.total(games.map { it.plays }))}"

        return lines
    }

    override fun statisticsMenuProvider(profile: PracticeProfile): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        val border = ItemBuilder.of(XMaterial.GRAY_STAINED_GLASS_PANE)
            .name(" ")
            .toButton()
        (0 until 45).forEach { buttons[it] = border }

        buttons[4] = ItemBuilder.of(XMaterial.NETHER_STAR)
            .name("${CC.BL_PURPLE}Arcade Statistics")
            .setLore(
                listOf(
                    "${CC.GRAY}Combined across all arcade games.",
                    "",
                    "${CC.GRAY}Total Wins: ${CC.WHITE}${Numbers.format(profile.total(games.map { it.wins }))}",
                    "${CC.GRAY}Total Losses: ${CC.WHITE}${Numbers.format(profile.totalLosses())}",
                    "${CC.GRAY}Total Kills: ${CC.WHITE}${Numbers.format(profile.total(games.map { it.kills }))}",
                    "${CC.GRAY}Games Played: ${CC.WHITE}${Numbers.format(profile.total(games.map { it.plays }))}",
                )
            )
            .toButton()

        games.forEachIndexed { index, game ->
            val lore = mutableListOf<String>()
            lore += "${CC.GRAY}Wins: ${CC.WHITE}${Numbers.format(profile.total(listOf(game.wins)))}"
            if (game.kills != null)
            {
                lore += "${CC.GRAY}Kills: ${CC.WHITE}${Numbers.format(profile.total(listOf(game.kills)))}"
            }
            game.extra.forEach { stat ->
                lore += "${CC.GRAY}${stat.label}: ${CC.WHITE}${Numbers.format(profile.total(listOf(stat.id)))}"
            }
            lore += "${CC.GRAY}Games Played: ${CC.WHITE}${Numbers.format(profile.total(listOf(game.plays)))}"

            buttons[cardSlots[index]] = ItemBuilder.of(game.icon)
                .name("${CC.BD_PURPLE}${game.displayName}")
                .setLore(lore)
                .toButton()
        }

        return buttons
    }

    override fun leaderboardsProvider(player: Player) = mapOf<Int, Button>()
}
