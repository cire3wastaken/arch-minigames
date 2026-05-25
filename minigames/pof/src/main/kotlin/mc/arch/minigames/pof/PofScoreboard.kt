package mc.arch.minigames.pof

import gg.tropic.practice.games.GameReport
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameScoreboard
import mc.arch.minigame.pof.PofGameConfiguration
import mc.arch.minigames.pof.state.PofPlayerResources
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import org.bukkit.entity.Player

class PofScoreboard(
    override val game: AbstractMiniGameGameImpl<PofGameConfiguration>,
    private val configuration: PofGameConfiguration,
    private val resourcesProvider: () -> Collection<PofPlayerResources>,
    private val remainingMs: () -> Long = { 0L },
    private val deathmatchActive: () -> Boolean = { false }
) : MiniGameScoreboard
{
    override fun titleFor(player: Player) = "${CC.B_AQUA}POF"

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
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Mode: ${CC.WHITE}${configuration.mode.displayName}"
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Waiting for more"
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}players to join..."
            middle += ""
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createStartingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Mode: ${CC.WHITE}${configuration.mode.displayName}"
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Starting in ${CC.B_WHITE}${game.startCountDown}s${CC.GRAY}..."
            middle += ""
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createInGameScoreboardFor(player: Player, board: MutableList<String>)
    {
        val all = resourcesProvider()
        val viewer = all.firstOrNull { it.player == player.uniqueId }

        board.surround { middle ->
            val aliveResources = all.filter { !it.spectator && it.toPlayer() != null }
            val aliveTeams = aliveResources.mapNotNull { it.team }.toSet().size

            middle += "${CC.AQUA}Stats:"
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Alive: ${CC.WHITE}${aliveResources.size}"
            if (configuration.mode.teamSize > 1)
            {
                middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Teams: ${CC.WHITE}$aliveTeams"
            }
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Kills: ${CC.WHITE}${viewer?.kills ?: 0}"
            middle += if (deathmatchActive())
            {
                "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}Sudden Death!"
            } else
            {
                "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Time left: ${CC.WHITE}${formatTime(remainingMs())}"
            }
            middle += ""

            val isAlive = viewer != null && !viewer.spectator
            middle += if (!isAlive)
            {
                "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}${Constants.X_SYMBOL}${CC.RED} You are dead!"
            } else
            {
                "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.B_GREEN}${Constants.CHECK_SYMBOL}${CC.GREEN} You are alive!"
            }
        }
    }

    override fun createEndingScoreboardFor(player: Player, report: GameReport?, board: MutableList<String>)
    {
        val all = resourcesProvider()
        val winner = all
            .filter { !it.spectator }
            .maxByOrNull { it.kills }
            ?: all.maxByOrNull { it.survivedUntil }

        board.surround { middle ->
            if (winner != null)
            {
                middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Winner:"
                middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.B_AQUA}${winner.username}"
                middle += ""
                middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Kills: ${CC.WHITE}${winner.kills}"
            } else
            {
                middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}No winner determined"
            }

            middle += ""
            middle += "${CC.AQUA}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Thanks for playing!"
        }
    }
}
