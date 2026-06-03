package mc.arch.minigames.arcade.sumo

import gg.tropic.practice.games.GameReport
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameScoreboard
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.math.Numbers
import net.evilblock.cubed.util.nms.MinecraftReflection
import org.bukkit.entity.Player

class SumoScoreboard(
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    private val configuration: ArcadeMiniGameConfiguration,
    private val lifecycle: SumoArcadeLifecycle
) : MiniGameScoreboard
{
    override fun titleFor(player: Player) = "${CC.BD_GREEN}SUMO"

    override fun createWaitingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Waiting for more"
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}players to join"
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}the game..."
            middle += ""
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createStartingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Starting in ${CC.B_WHITE}${game.startCountDown}s${CC.GRAY}..."
            middle += ""
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createInGameScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            val fighting = lifecycle.activelyFightingPlayers
            if (lifecycle.isInMatch && fighting != null)
            {
                val player1 = fighting.first.toPlayer()
                val player2 = fighting.second.toPlayer()

                middle += "${CC.GREEN}Fighting:"
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}${player1?.name ?: "Unknown"} ${CC.D_GRAY}(${
                    player1?.let { Numbers.format(MinecraftReflection.getPing(it)) } ?: "0"
                }ms)"
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}vs."
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}${player2?.name ?: "Unknown"} ${CC.D_GRAY}(${
                    player2?.let { Numbers.format(MinecraftReflection.getPing(it)) } ?: "0"
                }ms)"
            } else
            {
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GREEN}Preparing round..."
            }

            middle += ""
            middle += "${CC.GREEN}Game:"
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Round: ${CC.WHITE}#${lifecycle.roundNumber}"
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Remaining: ${CC.WHITE}${lifecycle.toNonEliminatedPlayers().size}/${configuration.maximumPlayers}"

            val playerResource = lifecycle.playerResources[player.uniqueId]
            val isAlive = playerResource != null && !playerResource.eliminated
            middle += ""
            middle += if (!isAlive)
            {
                "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}${Constants.X_SYMBOL}${CC.RED} You are dead!"
            } else
            {
                "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.B_GREEN}${Constants.CHECK_SYMBOL}${CC.GREEN} You are alive!"
            }
        }
    }

    override fun createEndingScoreboardFor(player: Player, report: GameReport?, board: MutableList<String>)
    {
        val winner = lifecycle.determineWinner()
        val winnerPlayer = winner?.toPlayer()

        board.surround { middle ->
            if (winnerPlayer != null)
            {
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Winner:"
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.B_WHITE}${winnerPlayer.name}"
            } else
            {
                middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}No winner determined"
            }

            middle += ""
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Thanks for playing!"
        }
    }
}
