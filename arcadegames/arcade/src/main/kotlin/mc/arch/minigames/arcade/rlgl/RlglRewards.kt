package mc.arch.minigames.arcade.rlgl

import gg.tropic.game.extensions.economy.Accounts
import gg.tropic.game.extensions.economy.Transaction
import gg.tropic.game.extensions.economy.TransactionService
import gg.tropic.game.extensions.economy.TransactionType
import gg.tropic.practice.statistics.StatisticService
import gg.tropic.practice.statistics.statisticWrite
import mc.arch.minigames.arcade.ArcadeMode
import mc.arch.minigames.arcade.ArcadeStatistic
import net.evilblock.cubed.util.CC
import java.util.UUID

object RlglRewards
{
    fun awardKill(killer: UUID)
    {
        StatisticService.update(killer) {
            statisticWrite(ArcadeStatistic.RLGL_KILLS.statisticID) { add(1) }
        }
    }

    fun awardFinish(player: UUID)
    {
        StatisticService.update(player) {
            statisticWrite(ArcadeStatistic.RLGL_FINISHES.statisticID) { add(1) }
        }
    }

    fun awardCaught(player: UUID)
    {
        StatisticService.update(player) {
            statisticWrite(ArcadeStatistic.RLGL_TIMES_CAUGHT.statisticID) { add(1) }
        }
    }

    fun awardFinishers(finishers: List<RlglPlayerResources>)
    {
        finishers.forEachIndexed { index, resources ->
            val place = index + 1
            val amount = when (place)
            {
                1 -> RlglConstants.WINNER_COIN_REWARD
                2 -> RlglConstants.RUNNERUP_COIN_REWARD
                3 -> RlglConstants.THIRD_PLACE_COIN_REWARD
                else -> RlglConstants.PARTICIPATION_COIN_REWARD
            }
            val xpAmount = when (place)
            {
                1 -> RlglConstants.WINNER_XP_REWARD
                2 -> RlglConstants.RUNNERUP_XP_REWARD
                3 -> RlglConstants.THIRD_PLACE_XP_REWARD
                else -> RlglConstants.PARTICIPATION_XP_REWARD
            }

            StatisticService.update(resources.player) {
                val stats = if (place == 1)
                    ArcadeStatistic.winOf(ArcadeMode.RED_LIGHT_GREEN_LIGHT)
                else
                    ArcadeStatistic.participationOf(ArcadeMode.RED_LIGHT_GREEN_LIGHT)
                statisticWrite(*stats) { add(1) }
            }

            deposit(resources.player, amount, xpAmount)
            val label = when (place)
            {
                1 -> "Winning a game"
                2 -> "2nd place"
                3 -> "3rd place"
                else -> "Finishing a game"
            }
            resources.toPlayer()?.sendMessage("${CC.D_PURPLE}+$amount Arcade Coins ${CC.L_PURPLE}+$xpAmount Arcade Experience ${CC.GRAY}($label)")
        }
    }

    fun awardParticipants(participants: Collection<RlglPlayerResources>, alreadyRewarded: Set<UUID>)
    {
        participants
            .filter { it.player !in alreadyRewarded }
            .forEach { resources ->
                StatisticService.update(resources.player) {
                    statisticWrite(*ArcadeStatistic.participationOf(ArcadeMode.RED_LIGHT_GREEN_LIGHT)) { add(1) }
                }
                deposit(resources.player, RlglConstants.PARTICIPATION_COIN_REWARD, RlglConstants.PARTICIPATION_XP_REWARD)
                resources.toPlayer()
                    ?.sendMessage("${CC.D_PURPLE}+${RlglConstants.PARTICIPATION_COIN_REWARD} Arcade Coins ${CC.L_PURPLE}+${RlglConstants.PARTICIPATION_XP_REWARD} Arcade Experience ${CC.GRAY}(Participating in a game)")
            }
    }

    private fun deposit(receiver: UUID, coins: Long, experience: Long)
    {
        TransactionService.submit(
            Transaction(
                sender = Accounts.SERVER,
                receiver = receiver,
                type = TransactionType.Deposit,
                economy = "arcade-coins",
                amount = coins
            )
        )
        TransactionService.submit(
            Transaction(
                sender = Accounts.SERVER,
                receiver = receiver,
                type = TransactionType.Deposit,
                economy = "arcade-experience",
                amount = experience
            )
        )
    }
}
