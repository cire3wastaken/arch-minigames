package mc.arch.minigames.arcade.oitc

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

object OitcRewards
{
    fun awardKill(killer: UUID, byBow: Boolean, bowDistance: Double?)
    {
        val longRange = byBow && bowDistance != null &&
            bowDistance >= OitcConstants.LONGRANGE_KILL_DISTANCE

        StatisticService.update(killer) {
            statisticWrite(ArcadeStatistic.OITC_KILLS.statisticID) { add(1) }
            if (byBow)
            {
                statisticWrite(ArcadeStatistic.OITC_BOW_KILLS.statisticID) { add(1) }
            }
            if (longRange)
            {
                statisticWrite(ArcadeStatistic.OITC_LONGRANGE_KILLS.statisticID) { add(1) }
            }
        }
    }

    fun awardWinner(winner: OitcPlayerResources)
    {
        StatisticService.update(winner.player) {
            statisticWrite(*ArcadeStatistic.winOf(ArcadeMode.OITC)) {
                add(1)
            }
        }

        deposit(winner.player, OitcConstants.WINNER_COIN_REWARD, OitcConstants.WINNER_XP_REWARD)
        winner.toPlayer()?.sendMessage("${CC.D_PURPLE}+${OitcConstants.WINNER_COIN_REWARD} Arcade Coins ${CC.L_PURPLE}+${OitcConstants.WINNER_XP_REWARD} Arcade Experience ${CC.GRAY}(Winning a game)")
    }

    fun awardParticipants(participants: Collection<OitcPlayerResources>, winnerId: UUID)
    {
        participants
            .filter { it.player != winnerId }
            .forEach { resources ->
                StatisticService.update(resources.player) {
                    statisticWrite(*ArcadeStatistic.participationOf(ArcadeMode.OITC)) {
                        add(1)
                    }
                }

                deposit(resources.player, OitcConstants.PARTICIPATION_COIN_REWARD, OitcConstants.PARTICIPATION_XP_REWARD)
                resources.toPlayer()
                    ?.sendMessage("${CC.D_PURPLE}+${OitcConstants.PARTICIPATION_COIN_REWARD} Arcade Coins ${CC.L_PURPLE}+${OitcConstants.PARTICIPATION_XP_REWARD} Arcade Experience ${CC.GRAY}(Participating in a game)")
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
