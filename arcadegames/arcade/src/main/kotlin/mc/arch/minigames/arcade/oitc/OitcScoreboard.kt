package mc.arch.minigames.arcade.oitc

import gg.tropic.practice.games.GameReport
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameScoreboard
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import org.bukkit.entity.Player

class OitcScoreboard(
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    private val configuration: ArcadeMiniGameConfiguration,
    private val killTarget: Int,
    private val resourcesProvider: () -> Collection<OitcPlayerResources>
) : MiniGameScoreboard
{
    override fun titleFor(player: Player) = "${CC.B_GOLD}OITC"

    override fun createWaitingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Waiting for more"
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}players to join"
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}the game..."
            middle += ""
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createStartingScoreboardFor(player: Player, board: MutableList<String>)
    {
        board.surround { middle ->
            middle += "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Starting in ${CC.B_WHITE}${game.startCountDown}s${CC.GRAY}..."
            middle += ""
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                game.allNonSpectators().size
            }/${configuration.maximumPlayers}"
        }
    }

    override fun createInGameScoreboardFor(player: Player, board: MutableList<String>)
    {
        val all = resourcesProvider()
        val viewer = all.firstOrNull { it.player == player.uniqueId }

        board.surround { middle ->
            val alive = all.count { !it.spectator && it.toPlayer() != null }

            middle += "${CC.GOLD}Stats:"
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Alive: ${CC.WHITE}$alive"
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Kills: ${CC.WHITE}${viewer?.kills ?: 0}${CC.D_GRAY}/${CC.WHITE}${killTarget}"
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Deaths: ${CC.WHITE}${viewer?.deaths ?: 0}"
            middle += ""

            val leaders = all
                .filter { !it.spectator }
                .sortedByDescending { it.kills }
                .take(3)

            if (leaders.isNotEmpty())
            {
                middle += "${CC.GOLD}Top kills:"
                leaders.forEachIndexed { index, resources ->
                    middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.WHITE}${index + 1}. ${resources.username} ${CC.D_GRAY}(${CC.WHITE}${resources.kills}${CC.D_GRAY})"
                }
                middle += ""
            }

            val isAlive = viewer != null && !viewer.spectator && !viewer.pendingRespawn
            middle += if (!isAlive)
            {
                "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.B_RED}${Constants.X_SYMBOL}${CC.RED} You are dead!"
            } else
            {
                "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.B_GREEN}${Constants.CHECK_SYMBOL}${CC.GREEN} You are alive!"
            }
        }
    }

    override fun createEndingScoreboardFor(player: Player, report: GameReport?, board: MutableList<String>)
    {
        val winner = resourcesProvider()
            .filter { !it.spectator }
            .maxByOrNull { it.kills }

        board.surround { middle ->
            if (winner != null)
            {
                middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Winner:"
                middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.B_WHITE}${winner.username}"
                middle += ""
                middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Kills: ${CC.WHITE}${winner.kills}"
            } else
            {
                middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}No winner determined"
            }

            middle += ""
            middle += "${CC.GOLD}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Thanks for playing!"
        }
    }
}
