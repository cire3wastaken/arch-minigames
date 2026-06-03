package gg.tropic.practice.menu

import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.minigame.MinigameLobby
import gg.tropic.practice.profile.PracticeProfile
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIdFrom
import gg.tropic.practice.statistics.valueOf
import gg.tropic.practice.statistics.numericalValueOf
import gg.tropic.practice.statresets.StatResetTokens
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType

/**
 * @author Elb1to
 * @since 10/19/2023
 */
class StatisticsMenu(
    player: Player,
    private val profile: PracticeProfile,
) : TemplateKitMenu(player)
{
    private var resetTokens = 0

    override fun getButtons(player: Player): MutableMap<Int, Button>
    {
        if (MinigameLobby.isMinigameLobby())
        {
            MinigameLobby.competitiveFor(player)?.let {
                return it.statisticsMenuProvider(profile).toMutableMap()
            }
        }

        return super.getButtons(player)
    }

    override fun asyncLoadResources(player: Player, callback: (Boolean) -> Unit)
    {
        if (profile.identifier == player.uniqueId)
        {
            Schedulers
                .async()
                .run {
                    resetTokens = StatResetTokens
                        .of(player.uniqueId)
                        .join() ?: 0
                }
                .thenRunAsync {
                    callback(true)
                }
            return
        }
    }

    override fun filterDisplayOfKit(player: Player, kit: Kit) = true
    override fun shouldIncludeKitDescription() = false

    override fun itemTitleFor(player: Player, kit: Kit) = "${CC.PRI}${kit.displayName}"

    override fun itemDescriptionOf(player: Player, kit: Kit): List<String>
    {
        val description = mutableListOf<String>()
        description += ""
        description += "${CC.GRAY}Games played: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Plays) {
                    kit(kit)
                }
            )
        }"
        description += "${CC.GRAY}Games won: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Wins) {
                    kit(kit)
                }
            )
        }"
        description += "${CC.GRAY}Games lost: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Losses) {
                    kit(kit)
                }
            )
        }"

        val killsID = statisticIdFrom(TrackedKitStatistic.Kills) {
            kit(kit)
        }

        val deathsID = statisticIdFrom(TrackedKitStatistic.Deaths) {
            kit(kit)
        }

        val killsValue = profile.numericalValueOf(killsID)
        val deathsValue = profile.numericalValueOf(deathsID)

        description += ""
        description += "${CC.GRAY}Kills: ${profile.valueOf(killsID)}"
        description += "${CC.GRAY}Deaths: ${profile.valueOf(deathsID)}"
        description += "${CC.GRAY}K/D Ratio: ${
            if (deathsValue != null && killsValue != null && deathsValue.score.toLong() != 0L)
            {
                "${CC.WHITE}${
                    "%.2f".format(
                        (killsValue.score / deathsValue.score).toFloat()
                    )
                }"
            }
            else
            {
                "${CC.WHITE}0.00"
            }
        }"
        description += ""
        description += "${CC.GREEN}Casual Statistics:"
        description += "${CC.GRAY}Games played: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Plays) {
                    kit(kit)
                    casual()
                }
            )
        }"
        description += "${CC.GRAY}Games won: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Wins) {
                    kit(kit)
                    casual()
                }
            )
        }"
        description += "${CC.GRAY}Games won (Today): ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Wins) {
                    kit(kit)
                    casual()
                    daily()
                }
            )
        }"
        description += "${CC.GRAY}Games lost: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Losses) {
                    kit(kit)
                    casual()
                }
            )
        }"
        description += ""
        description += "${CC.GRAY}Longest winstreak: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.WinStreakHighest) {
                    kit(kit)
                    casual()
                }
            )
        }"
        description += "${CC.GRAY}Current winstreak: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.WinStreak) {
                    kit(kit)
                    casual()
                }
            )
        }"
        description += "${CC.GRAY}Current winstreak (Today): ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.WinStreak) {
                    kit(kit)
                    daily()
                    casual()
                }
            )
        }"

        if (kit.features(FeatureFlag.Ranked))
        {
            description += ""
            description += "${CC.AQUA}Ranked Statistics:"
            description += "${CC.GRAY}Ranked ELO: ${
                profile.valueOf(
                    statisticIdFrom(TrackedKitStatistic.ELO) {
                        kit(kit)
                        ranked()
                    }
                )
            }"
            description += ""
            description += "${CC.GRAY}Games played: ${
                profile.valueOf(
                    statisticIdFrom(TrackedKitStatistic.Plays) {
                        kit(kit)
                        ranked()
                    }
                )
            }"
            description += "${CC.GRAY}Games won: ${
                profile.valueOf(
                    statisticIdFrom(TrackedKitStatistic.Wins) {
                        kit(kit)
                        ranked()
                    }
                )
            }"
            description += "${CC.GRAY}Games won (Today): ${
                profile.valueOf(
                    statisticIdFrom(TrackedKitStatistic.Wins) {
                        kit(kit)
                        ranked()
                        daily()
                    }
                )
            }"
            description += "${CC.GRAY}Games lost: ${
                profile.valueOf(
                    statisticIdFrom(TrackedKitStatistic.Losses) {
                        kit(kit)
                        ranked()
                    }
                )
            }"
        }

        return description
    }

    override fun itemClicked(player: Player, kit: Kit, type: ClickType)
    {

    }

    override fun getGlobalButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()
        val description = mutableListOf<String>()
        description += ""
        description += "${CC.GRAY}Games played: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Plays) {

                }
            )
        }"
        description += "${CC.GRAY}Games won: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Wins) {

                }
            )
        }"
        description += "${CC.GRAY}Games lost: ${
            profile.valueOf(
                statisticIdFrom(TrackedKitStatistic.Losses) {

                }
            )
        }"

        val killsID = statisticIdFrom(TrackedKitStatistic.Kills) { }
        val deathsID = statisticIdFrom(TrackedKitStatistic.Deaths) { }

        val killsValue = profile.numericalValueOf(killsID)
        val deathsValue = profile.numericalValueOf(deathsID)

        description += ""
        description += "${CC.GRAY}Kills: ${profile.valueOf(killsID)}"
        description += "${CC.GRAY}Deaths: ${profile.valueOf(deathsID)}"
        description += "${CC.GRAY}K/D Ratio: ${
            if (deathsValue != null && killsValue != null && deathsValue.score.toLong() != 0L)
            {
                "${CC.WHITE}${
                    "%.2f".format(
                        (killsValue.score / deathsValue.score).toFloat()
                    )
                }"
            }
            else
            {
                "${CC.WHITE}0.00"
            }
        }"

        buttons[4] = ItemBuilder
            .of(Material.SIGN)
            .name("${CC.GREEN}Overall Statistics")
            .setLore(description)
            .toButton()

        if (player.uniqueId == profile.identifier)
        {
            buttons[40] = ItemBuilder
                .of(Material.BOOK)
                .name("${CC.GREEN}Stat Reset Tokens")
                .addToLore("${CC.GRAY}Unhappy with your stats?")
                .addToLore("${CC.GRAY}Purchase a reset token")
                .addToLore("${CC.GRAY}on ${CC.WHITE}store.arch.mc")
                .addToLore("")
                .addToLore("${CC.GREEN}You have $resetTokens reset token${
                    if (resetTokens == 1) "" else "s"
                }.")
                .toButton()
        }

        return buttons
    }

    override fun getPrePaginatedTitle(player: Player) = "${profile.identifier.username()}'s Statistics"
}
