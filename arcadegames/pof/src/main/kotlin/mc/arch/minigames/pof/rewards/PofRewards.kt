package mc.arch.minigames.pof.rewards

import gg.tropic.game.extensions.economy.Accounts
import gg.tropic.game.extensions.economy.Transaction
import gg.tropic.game.extensions.economy.TransactionService
import gg.tropic.game.extensions.economy.TransactionType
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.statistics.StatisticService
import gg.tropic.practice.statistics.statisticWrite
import mc.arch.minigames.pof.services.CorePofStatistic
import mc.arch.minigames.pof.state.PofPlayerResources
import net.evilblock.cubed.util.CC
import java.util.UUID

object PofRewards
{
    private const val WINNER_COIN_REWARD = 350L
    private const val WINNER_XP_REWARD = 100L
    private const val PARTICIPATION_COIN_REWARD = 35L
    private const val PARTICIPATION_XP_REWARD = 30L

    fun awardWinners(winners: Collection<PofPlayerResources>, mode: MiniGameModeMetadata)
    {
        winners.forEach { winner ->
            recordParticipation(winner, mode, win = true)
            deposit(winner.player, WINNER_COIN_REWARD, WINNER_XP_REWARD)
            winner.toPlayer()?.sendMessage("${CC.D_PURPLE}+$WINNER_COIN_REWARD Arcade Coins ${CC.L_PURPLE}+$WINNER_XP_REWARD Arcade Experience ${CC.GRAY}(Winning a game)")
        }
    }

    fun awardParticipants(
        participants: Collection<PofPlayerResources>,
        winners: Set<UUID>,
        mode: MiniGameModeMetadata
    )
    {
        participants
            .filter { it.player !in winners }
            .forEach { resources ->
                recordParticipation(resources, mode, win = false)
                deposit(resources.player, PARTICIPATION_COIN_REWARD, PARTICIPATION_XP_REWARD)
                resources.toPlayer()?.sendMessage("${CC.D_PURPLE}+$PARTICIPATION_COIN_REWARD Arcade Coins ${CC.L_PURPLE}+$PARTICIPATION_XP_REWARD Arcade Experience ${CC.GRAY}(Participating in a game)")
            }
    }

    private fun recordParticipation(resources: PofPlayerResources, mode: MiniGameModeMetadata, win: Boolean)
    {
        StatisticService.update(resources.player) {
            statisticWrite(
                CorePofStatistic.PLAYS.toCore(), CorePofStatistic.PLAYS.toMode(mode),
                CorePofStatistic.PLAYS_DAILY.toCore(), CorePofStatistic.PLAYS_DAILY.toMode(mode)
            ) {
                add(1)
            }

            if (win)
            {
                statisticWrite(
                    CorePofStatistic.WINS.toCore(), CorePofStatistic.WINS.toMode(mode),
                    CorePofStatistic.WINS_WEEKLY.toCore(), CorePofStatistic.WINS_WEEKLY.toMode(mode),
                    CorePofStatistic.WINS_DAILY.toCore(), CorePofStatistic.WINS_DAILY.toMode(mode),
                    CorePofStatistic.WIN_STREAK.toCore(), CorePofStatistic.WIN_STREAK.toMode(mode)
                ) {
                    add(1)
                }
            } else
            {
                statisticWrite(CorePofStatistic.LOSSES.toCore(), CorePofStatistic.LOSSES.toMode(mode)) {
                    add(1)
                }
                statisticWrite(
                    CorePofStatistic.WIN_STREAK.toCore(), CorePofStatistic.WIN_STREAK.toMode(mode)
                ) {
                    update(0)
                }
            }

            if (resources.kills > 0)
            {
                statisticWrite(
                    CorePofStatistic.KILLS.toCore(), CorePofStatistic.KILLS.toMode(mode),
                    CorePofStatistic.KILLS_DAILY.toCore(), CorePofStatistic.KILLS_DAILY.toMode(mode)
                ) {
                    add(resources.kills.toLong())
                }
            }
            if (resources.deaths > 0)
            {
                statisticWrite(CorePofStatistic.DEATHS.toCore(), CorePofStatistic.DEATHS.toMode(mode)) {
                    add(resources.deaths.toLong())
                }
            }
            if (resources.blocksPlaced > 0)
            {
                statisticWrite(CorePofStatistic.BLOCKS_PLACED.toCore(), CorePofStatistic.BLOCKS_PLACED.toMode(mode)) {
                    add(resources.blocksPlaced.toLong())
                }
            }
            if (resources.lootPickedUp > 0)
            {
                statisticWrite(
                    CorePofStatistic.LOOT_PICKED_UP.toCore(), CorePofStatistic.LOOT_PICKED_UP.toMode(mode),
                    CorePofStatistic.LOOT_PICKED_UP_WEEKLY.toCore(), CorePofStatistic.LOOT_PICKED_UP_WEEKLY.toMode(mode)
                ) {
                    add(resources.lootPickedUp.toLong())
                }
            }
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
