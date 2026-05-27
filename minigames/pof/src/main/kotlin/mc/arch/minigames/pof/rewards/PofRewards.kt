package mc.arch.minigames.pof.rewards

import gg.tropic.game.extensions.economy.Accounts
import gg.tropic.game.extensions.economy.Transaction
import gg.tropic.game.extensions.economy.TransactionService
import gg.tropic.game.extensions.economy.TransactionType
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.statistics.StatisticService
import gg.tropic.practice.statistics.statisticWrite
import mc.arch.minigame.pof.services.CorePofStatistic
import mc.arch.minigames.pof.state.PofPlayerResources
import net.evilblock.cubed.util.CC
import java.util.UUID

object PofRewards
{
    private const val WINNER_COIN_REWARD = 350L
    private const val WINNER_POF_COIN_REWARD = 100L
    private const val WINNER_XP_REWARD = 100L
    private const val WINNER_POF_XP_REWARD = 100L
    private const val PARTICIPATION_COIN_REWARD = 35L
    private const val PARTICIPATION_POF_COIN_REWARD = 10L
    private const val PARTICIPATION_XP_REWARD = 30L
    private const val PARTICIPATION_POF_XP_REWARD = 30L

    fun awardWinners(winners: Collection<PofPlayerResources>, mode: MiniGameModeMetadata)
    {
        winners.forEach { winner ->
            recordParticipation(winner, mode, win = true)
            deposit(winner.player, "coins", WINNER_COIN_REWARD)
            deposit(winner.player, "pof-coins", WINNER_POF_COIN_REWARD)
            deposit(winner.player, "experience", WINNER_XP_REWARD)
            deposit(winner.player, "pof-experience", WINNER_POF_XP_REWARD)
            winner.toPlayer()?.apply {
                sendMessage("${CC.GOLD}+$WINNER_COIN_REWARD Coins (Winning Pillar of Fortune)")
                sendMessage("${CC.GOLD}+$WINNER_POF_COIN_REWARD Pillar of Fortune Coins (Winning Pillar of Fortune)")
                sendMessage("${CC.GREEN}+$WINNER_XP_REWARD Experience (Winning Pillar of Fortune)")
                sendMessage("${CC.RED}+$WINNER_POF_XP_REWARD Pillar of Fortune Experience (Winning Pillar of Fortune)")
            }
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
                deposit(resources.player, "coins", PARTICIPATION_COIN_REWARD)
                deposit(resources.player, "pof-coins", PARTICIPATION_POF_COIN_REWARD)
                deposit(resources.player, "experience", PARTICIPATION_XP_REWARD)
                deposit(resources.player, "pof-experience", PARTICIPATION_POF_XP_REWARD)
                resources.toPlayer()?.apply {
                    sendMessage("${CC.GOLD}+$PARTICIPATION_COIN_REWARD Coins (Playing Pillar of Fortune)")
                    sendMessage("${CC.GOLD}+$PARTICIPATION_POF_COIN_REWARD Pillar of Fortune Coins (Playing Pillar of Fortune)")
                    sendMessage("${CC.GREEN}+$PARTICIPATION_XP_REWARD Experience (Playing Pillar of Fortune)")
                    sendMessage("${CC.RED}+$PARTICIPATION_POF_XP_REWARD Pillar of Fortune Experience (Playing Pillar of Fortune)")
                }
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

    private fun deposit(receiver: UUID, economy: String, amount: Long)
    {
        TransactionService.submit(
            Transaction(
                sender = Accounts.SERVER,
                receiver = receiver,
                type = TransactionType.Deposit,
                economy = economy,
                amount = amount
            )
        )
    }
}
