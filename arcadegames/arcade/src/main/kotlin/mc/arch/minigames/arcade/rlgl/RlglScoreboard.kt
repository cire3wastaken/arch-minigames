package mc.arch.minigames.arcade.rlgl

import gg.tropic.practice.games.GameReport
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameScoreboard
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import org.bukkit.entity.Player

class RlglScoreboard(
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    private val configuration: ArcadeMiniGameConfiguration,
    private val resourcesProvider: () -> Collection<RlglPlayerResources>,
    private val remainingMs: () -> Long,
    private val isRedLight: () -> Boolean,
    private val finishCount: () -> Int,
    private val finishTarget: Int
) : MiniGameScoreboard
{
    override fun titleFor(player: Player) = "${CC.B_RED}Red Light Green Light"

    private fun formatTime(ms: Long): String
    {
        val total = (ms / 1000).coerceAtLeast(0)
        val mm = total / 60
        val ss = total % 60
        return "$mm:${ss.toString().padStart(2, '0')}"
    }

    override fun createWaitingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Waiting for more"
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}players to join"
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}the game..."
            middle += ""
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createStartingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Starting in ${CC.B_WHITE}${game.startCountDown}s${CC.GRAY}..."
            middle += ""
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createInGameScoreboardFor(player: Player, board: MutableList<String>)
    {
        val all = resourcesProvider()
        val viewer = all.firstOrNull { it.player == player.uniqueId }
        val alive = all.count { !it.spectator && it.toPlayer() != null }
        val red = isRedLight()
        val lightLine = if (red)
            "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}RED LIGHT"
        else
            "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_GREEN}GREEN LIGHT"

        board.surround { middle ->
            middle += lightLine
            middle += ""
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Alive: ${CC.WHITE}$alive"
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Finished: ${CC.WHITE}${finishCount()}/${finishTarget}"
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Time left: ${CC.WHITE}${formatTime(remainingMs())}"
            middle += ""
            val viewerFinished = (viewer?.crossedFinishAt ?: 0L) != 0L
            middle += when
            {
                viewer == null || viewer.spectator && !viewerFinished ->
                    "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}${Constants.X_SYMBOL}${CC.RED} You are out!"
                viewerFinished ->
                    "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_GOLD}${Constants.CHECK_SYMBOL}${CC.GOLD} Finished #${viewer.finishPlace}!"
                else ->
                    "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_GREEN}${Constants.CHECK_SYMBOL}${CC.GREEN} You are alive!"
            }
        }
    }

    override fun createEndingScoreboardFor(player: Player, report: GameReport?, board: MutableList<String>)
    {
        val all = resourcesProvider()
        val winner = all
            .filter { it.crossedFinishAt != 0L }
            .minByOrNull { it.crossedFinishAt }

        board.surround { middle ->
            if (winner != null)
            {
                middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Winner:"
                middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.B_GOLD}${winner.username}"
            } else
            {
                middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}No winner."
            }
            middle += ""
            middle += "${CC.RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Thanks for playing!"
        }
    }
}
