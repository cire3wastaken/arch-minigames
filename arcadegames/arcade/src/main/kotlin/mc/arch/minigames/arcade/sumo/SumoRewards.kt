package mc.arch.minigames.arcade.sumo

import gg.tropic.game.extensions.economy.Accounts
import gg.tropic.game.extensions.economy.Transaction
import gg.tropic.game.extensions.economy.TransactionService
import gg.tropic.game.extensions.economy.TransactionType
import gg.tropic.practice.statistics.StatisticService
import gg.tropic.practice.statistics.statisticWrite
import mc.arch.minigames.arcade.ArcadeMode
import mc.arch.minigames.arcade.ArcadeStatistic
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player
import java.util.UUID

object SumoRewards
{
    fun awardEliminated(eliminated: Player)
    {
        StatisticService.update(eliminated.uniqueId) {
            statisticWrite(*ArcadeStatistic.participationOf(ArcadeMode.SUMO)) {
                add(1)
            }
        }

        deposit(eliminated.uniqueId, SumoConstants.PARTICIPATION_COIN_REWARD, SumoConstants.PARTICIPATION_XP_REWARD)
        eliminated.sendMessage("${CC.D_PURPLE}+${SumoConstants.PARTICIPATION_COIN_REWARD} Arcade Coins ${CC.GRAY}(Participating in a game)")
        eliminated.sendMessage("${CC.L_PURPLE}+${SumoConstants.PARTICIPATION_XP_REWARD} Arcade Experience ${CC.GRAY}(Participating in a game)")
    }

    fun awardKnockoff(winner: SumoPlayerResources)
    {
        winner.roundWins += 1
        val streak = winner.roundWins.toLong()

        StatisticService.update(winner.player) {
            statisticWrite(ArcadeStatistic.SUMO_KNOCKOFFS.statisticID) {
                add(1)
            }
            statisticWrite(ArcadeStatistic.SUMO_BEST_STREAK.statisticID) {
                if (streak > scoreAndPosition().score.toLong())
                {
                    update(streak)
                }
            }
        }
    }

    fun awardWinner(winner: SumoPlayerResources)
    {
        StatisticService.update(winner.player) {
            statisticWrite(*ArcadeStatistic.winOf(ArcadeMode.SUMO)) {
                add(1)
            }
        }

        deposit(winner.player, SumoConstants.WINNER_COIN_REWARD, SumoConstants.WINNER_XP_REWARD)
        winner.toPlayer()?.sendMessage("${CC.D_PURPLE}+${SumoConstants.WINNER_COIN_REWARD} Arcade Coins ${CC.GRAY}(Winning a game)")
        winner.toPlayer()?.sendMessage("${CC.L_PURPLE}+${SumoConstants.WINNER_XP_REWARD} Arcade Experience ${CC.GRAY}(Winning a game)")
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
