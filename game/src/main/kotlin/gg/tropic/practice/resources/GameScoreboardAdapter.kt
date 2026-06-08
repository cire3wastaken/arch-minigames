package gg.tropic.practice.resources

import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.agnostic.sync.ServerSync.getLocalGameServer
import gg.scala.flavor.service.Service
import gg.scala.lemon.LemonConstants
import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.practice.Globals
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.settings.layout
import gg.tropic.practice.settings.scoreboard.ScoreboardStyle
import gg.tropic.practice.extensions.colorOf
import gg.tropic.practice.extensions.toRBTeamSide
import gg.tropic.practice.ugc.WorldInstanceProviderType
import gg.tropic.practice.ugc.toHostedWorld
import net.evilblock.cubed.scoreboard.ScoreboardAdapter
import net.evilblock.cubed.scoreboard.ScoreboardAdapterRegister
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.nms.MinecraftReflection
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*

/**
 * @author GrowlyX
 * @since 8/5/2022
 */
@Service
@ScoreboardAdapterRegister
object GameScoreboardAdapter : ScoreboardAdapter()
{
    override fun getLines(
        board: LinkedList<String>, player: Player
    )
    {
        val layout: ScoreboardStyle = layout(player)

        if (layout == ScoreboardStyle.Disabled)
        {
            return
        }

        val hostedWorld = player.toHostedWorld()
        if (hostedWorld != null && hostedWorld.providerType != WorldInstanceProviderType.PRISON)
        {
            board += hostedWorld.generateScoreboardLines(player)
            return
        }

        val game = GameService
            .byPlayerOrSpectator(player.uniqueId)
            ?: return

        if (game.lifecycle() != GameLifecycle.MiniGame)
        {
            board += ""
        }

        fun TeamIdentifier.format() = if (game.flag(FeatureFlag.RedBlueTeams)) toRBTeamSide().display[0] else label

        fun insertMRStatusSection(firstPerson: Boolean = true)
        {
            val self = if (firstPerson) game.getTeamOf(player) else game.teams.first()
            val selfColor = game.colorOf(self)

            val them = game.getOpponent(self)
            val theirColor = game.colorOf(them)

            val teamSection = mutableListOf<String>()
            teamSection += "${CC.PRI}Teams: "

            val red = game.teams.first()
            val blue = game.teams.last()

            when (game.lifecycle())
            {
                GameLifecycle.SoulBound ->
                {
                }

                GameLifecycle.MiniGame ->
                {
                }

                GameLifecycle.RoundBound ->
                {
                    if (game.flag(FeatureFlag.RedBlueTeams))
                    {
                        val roundsRequiredForCompletion = game
                            .flagMetaData(FeatureFlag.RoundsRequiredToCompleteGame, "value")
                            ?.toIntOrNull() ?: 2

                        var ballID = ""
                        repeat(red.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.RED}⬤"
                        }

                        repeat(roundsRequiredForCompletion - red.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.GRAY}⬤"
                        }

                        teamSection += "${CC.RED}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Red: ${CC.RED}$ballID${
                            if (red.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"

                        ballID = ""
                        repeat(blue.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.BLUE}⬤"
                        }

                        repeat(roundsRequiredForCompletion - blue.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.GRAY}⬤"
                        }

                        teamSection += "${CC.BLUE}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Blue: ${CC.BLUE}$ballID${
                            if (blue.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"
                    } else
                    {
                        val roundsRequiredForCompletion = game
                            .flagMetaData(FeatureFlag.RoundsRequiredToCompleteGame, "value")
                            ?.toIntOrNull() ?: 2

                        var ballID = ""
                        repeat(self.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "$selfColor⬤"
                        }

                        repeat(roundsRequiredForCompletion - self.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.GRAY}⬤"
                        }

                        teamSection += "${CC.WHITE}${if (!firstPerson) self.teamIdentifier.format() else "You"}: $ballID"

                        ballID = ""
                        repeat(them.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "$theirColor⬤"
                        }

                        repeat(roundsRequiredForCompletion - them.gameLifecycleArbitraryObjectiveProgress)
                        {
                            ballID += "${CC.GRAY}⬤"
                        }

                        teamSection += "${CC.WHITE}${if (!firstPerson) them.teamIdentifier.format() else "Them"}: $ballID"
                    }
                }

                GameLifecycle.ObjectiveBound ->
                {
                    if (game.flag(FeatureFlag.RedBlueTeams))
                    {
                        teamSection += "${CC.RED}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Red: ${CC.RED}${
                            red.gameLifecycleArbitraryObjectiveProgress
                        }${
                            if (red.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"
                        teamSection += "${CC.BLUE}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Blue: ${CC.BLUE}${
                            blue.gameLifecycleArbitraryObjectiveProgress
                        }${
                            if (blue.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"
                    } else
                    {
                        teamSection += "${CC.GRAY}${if (!firstPerson) self.teamIdentifier.format() else "You"}: $selfColor${
                            self.gameLifecycleArbitraryObjectiveProgress
                        }"
                        teamSection += "${CC.GRAY}${if (!firstPerson) them.teamIdentifier.format() else "Them"}: $theirColor${
                            them.gameLifecycleArbitraryObjectiveProgress
                        }"
                    }
                }

                GameLifecycle.ObjectivePlusSoulBound ->
                {
                    if (game.flag(FeatureFlag.RedBlueTeams))
                    {
                        teamSection += "${CC.RED}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Red: ${CC.RED}${
                            if (game.gameLifecycleObjectiveMet(blue)) "${red.nonSpectators().size}" else "✔"
                        }${
                            if (red.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"
                        teamSection += "${CC.BLUE}${Constants.THICK_VERTICAL_LINE} ${CC.WHITE}Blue: ${CC.BLUE}${
                            if (game.gameLifecycleObjectiveMet(red)) "${blue.nonSpectators().size}" else "✔"
                        }${
                            if (blue.teamIdentifier == self.teamIdentifier && firstPerson) " ${CC.GRAY}YOU" else ""
                        }"
                    } else
                    {
                        teamSection += "${CC.GRAY}You: $selfColor${
                            if (game.gameLifecycleObjectiveMet(them)) "${self.nonSpectators().size}" else "✔"
                        }"
                        teamSection += "${CC.GRAY}Them: $theirColor${
                            if (game.gameLifecycleObjectiveMet(self)) "${them.nonSpectators().size}" else "✔"
                        }"
                    }
                }
            }

            teamSection += ""
            board.addAll(1, teamSection)
        }

        if (game.miniGameLifecycle == null && player.uniqueId in game.expectedSpectators)
        {
            if (game.lifecycle() == GameLifecycle.SoulBound || game.lifecycle() == GameLifecycle.MiniGame)
            {
                for (team in (if (game.robot()) listOf(game.humanSide()) else game.teams))
                {
                    fun Player.format() = "${CC.WHITE}${
                        if (GameService.isSpectating(this)) CC.STRIKE_THROUGH else ""
                    }$name ${CC.GRAY}(${
                        MinecraftReflection.getPing(this)
                    }ms)"

                    val bukkitPlayers = team.toBukkitPlayers().filterNotNull()
                    val sidePrefix = "${game.colorOf(team)}${Constants.THIN_VERTICAL_LINE} ${CC.BOLD}${team.teamIdentifier.format()}"

                    board += "$sidePrefix ${
                        if (bukkitPlayers.size == 1) bukkitPlayers.first().format() else ""
                    }"

                    if (bukkitPlayers.size > 1)
                    {
                        bukkitPlayers
                            .take(2)
                            .forEach {
                                board += "- ${it.format()}"
                            }

                        board += ""
                    }
                }
            }

            if (board.last() != "")
            {
                board += ""
            }

            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Map: ${CC.WHITE}${game.map.displayName}"
            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Type: ${CC.WHITE}${game.expectationModel.queueType ?: "Duel"}"
            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Duration: ${CC.WHITE}${game.getDuration()}"

            if (!(game.lifecycle() == GameLifecycle.SoulBound || game.lifecycle() == GameLifecycle.MiniGame))
            {
                insertMRStatusSection(firstPerson = false)
            }
        } else
        {
            if (game.miniGameLifecycle == null)
            {
                when (game.state)
                {
                    GameState.Waiting ->
                    {
                        board += "${CC.GRAY}Waiting for players..."
                    }

                    GameState.Starting ->
                    {
                        val opponents = if (game.isFreeForAll)
                            game.getTeamOf(player).players
                                .filterNot { it == player.uniqueId }
                        else
                            game
                                .getAllOpponents(game.getTeamOf(player))
                                .flatMap(GameTeam::players)
                        board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Starting in: ${CC.WHITE}${game.currentGameStartCountdown}s"

                        if (game.isFreeForAll)
                        {
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Players: ${CC.WHITE}${opponents.size + 1}"
                            board += "${CC.GRAY}Opponents:"

                            for (other in opponents.take(4))
                            {
                                board += "${CC.GRAY} - ${CC.WHITE}${game.usernameOf(other)}"
                            }

                            if (opponents.size > 4)
                            {
                                board += "${CC.WHITE}(and ${opponents.size - 4} more...)"
                            }
                        } else if (opponents.size == 1)
                        {
                            //todo: test with usernames above 16 char
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Opponent: ${CC.WHITE}${
                                if (game.robot())
                                    game.robotInstance.first().name()
                                else
                                    game.usernameOf(opponents.first())
                            }"
                        } else
                        {
                            board += "${CC.GRAY}Opponents:"

                            for (other in opponents.take(2))
                            {
                                val specificOpponent = if (game.robot())
                                {
                                    game.robotInstance.firstOrNull { it.solaraID() == other }?.name() ?: "Robot"
                                } else
                                {
                                    game.usernameOf(other)
                                }

                                board += "${CC.GRAY} - ${CC.WHITE}$specificOpponent"
                            }

                            if (opponents.size > 2)
                            {
                                board += "${CC.WHITE}(and ${opponents.size - 2} more...)"
                            }
                        }

                        board += ""
                        board += "${CC.PRI}Ping:"
                        board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}You: ${CC.GREEN}${MinecraftReflection.getPing(player)}ms"

                        if (!game.robot() && !game.isFreeForAll)
                        {
                            val opponentPlayer = opponents.firstOrNull()
                            if (opponentPlayer != null)
                            {
                                board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Them: ${CC.RED}${
                                    if (Bukkit.getPlayer(opponentPlayer) != null)
                                        MinecraftReflection.getPing(
                                            Bukkit.getPlayer(opponentPlayer)
                                        ) else "0"
                                }ms"
                            }
                        }
                    }

                    GameState.Playing ->
                    {
                        if (game.lifecycle() != GameLifecycle.MiniGame && game.isFreeForAll)
                        {
                            val team = game.getTeamOf(player)
                            val aliveOpponents = team.nonSpectators()
                                .filterNot { it.uniqueId == player.uniqueId }

                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Players left: ${CC.WHITE}${team.nonSpectators().size}"
                            board += ""
                            board += "${CC.PRI}Ping:"
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}You: ${CC.GREEN}${MinecraftReflection.getPing(player)}ms"
                            board += ""

                            if (aliveOpponents.isEmpty())
                            {
                                board += "${CC.GRAY}No opponents left!"
                            } else
                            {
                                board += "${CC.GRAY}Opponents:"
                                for (other in aliveOpponents.take(4))
                                {
                                    board += "${CC.GRAY} - ${CC.WHITE}${other.name}${CC.R} ${CC.D_GRAY}(${
                                        MinecraftReflection.getPing(other)
                                    }ms)"
                                }

                                if (aliveOpponents.size > 4)
                                {
                                    board += "${CC.WHITE}(and ${aliveOpponents.size - 4} more...)"
                                }
                            }

                            board += ""
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Map: ${CC.WHITE}${game.map.displayName}"
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Duration: ${CC.WHITE}${game.getDuration()}"
                        } else if (game.lifecycle() != GameLifecycle.MiniGame)
                        {
                            val opponent = game.getOpponent(player)
                            val showHitScoreboard = game.flag(FeatureFlag.WinWhenNHitsReached)
                            if (opponent.players.size == 1)
                            {
                                val opponentPlayer = opponent.players.first()
                                if (showHitScoreboard)
                                {
                                    val teamOfPlayer = game.getTeamOf(player)
                                    val teamOfOpponent = game.getOpponent(player)!!
                                    val hitsDiff =
                                        teamOfPlayer.gameLifecycleArbitraryObjectiveProgress - teamOfOpponent.gameLifecycleArbitraryObjectiveProgress

                                    val playerCombo = teamOfPlayer.playerCombos[player.uniqueId] ?: 0

                                    board += "${CC.PRI}Hits:"
                                    board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}You: ${CC.GREEN}${
                                        teamOfPlayer.gameLifecycleArbitraryObjectiveProgress
                                    }${
                                        if (hitsDiff == 0)
                                            ""
                                        else
                                            if (hitsDiff > 0)
                                                " ${CC.GREEN}(+$hitsDiff)"
                                            else
                                                " ${CC.RED}($hitsDiff)"
                                    }"
                                    board += " ${
                                        if (playerCombo == 0)
                                            "${CC.D_GRAY}No combo"
                                        else
                                            if (playerCombo > 0)
                                                "${CC.GREEN}+$playerCombo combo"
                                            else
                                                ""
                                    }"

                                    board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Them: ${CC.RED}${
                                        teamOfOpponent.gameLifecycleArbitraryObjectiveProgress
                                    }"
                                } else
                                {
                                    if (game.lifecycle() != GameLifecycle.SoulBound)
                                    {
                                        insertMRStatusSection(firstPerson = true)
                                    }

                                    board += "${CC.PRI}Ping:"
                                    board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}You: ${CC.GREEN}${MinecraftReflection.getPing(player)}ms"
                                    board += if (!game.robot())
                                    {
                                        "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Them: ${CC.RED}${
                                            if (Bukkit.getPlayer(opponentPlayer) != null)
                                                MinecraftReflection.getPing(
                                                    Bukkit.getPlayer(opponentPlayer)
                                                ) else "0"
                                        }ms"
                                    } else
                                    {
                                        "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Them: ${CC.RED}${game.robotInstance.first().ping()}ms"
                                    }
                                }

                                board += ""
                                board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Opponent: ${CC.WHITE}${
                                    if (game.robot()) game.robotInstance.first().name() else game.usernameOf(opponentPlayer)
                                }"
                                board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Duration: ${CC.WHITE}${game.getDuration()}"
                            } else
                            {
                                if (game.lifecycle() != GameLifecycle.SoulBound)
                                {
                                    insertMRStatusSection(firstPerson = true)
                                }

                                fun appendExtendedOpponentTeamDescription()
                                {
                                    if (!game.robot())
                                    {
                                        for (other in opponent.players.take(2))
                                        {
                                            val bukkitPlayer = Bukkit.getPlayer(other)
                                                ?: continue

                                            board += "${CC.GRAY} - ${CC.WHITE}${
                                                if (GameService.isSpectating(bukkitPlayer)) CC.STRIKE_THROUGH else ""
                                            }${other.username()}${CC.R} ${CC.D_GRAY}(${
                                                MinecraftReflection.getPing(bukkitPlayer)
                                            }ms)"
                                        }
                                    } else
                                    {
                                        for (other in opponent.players.take(2))
                                        {
                                            val instance = game.robot(other)
                                                ?: continue
                                            board += "${CC.GRAY} - ${CC.WHITE}${instance.name()}${CC.R} ${CC.D_GRAY}(${instance.ping()}ms)"
                                        }
                                    }

                                    if (opponent.players.size > 2)
                                    {
                                        board += "${CC.WHITE}(and ${opponent.players.size - 2} more...)"
                                    }
                                }

                                if (!showHitScoreboard)
                                {
                                    board += "${CC.PRI}Ping:"
                                    board += "${CC.GRAY}You: ${CC.GREEN}${MinecraftReflection.getPing(player)}ms"
                                    board += "${CC.GRAY}Them:"
                                    appendExtendedOpponentTeamDescription()
                                } else
                                {
                                    val teamOfPlayer = game.getTeamOf(player)
                                    val teamOfOpponent = game.getOpponent(player)
                                    val hitsDiff =
                                        teamOfPlayer.gameLifecycleArbitraryObjectiveProgress - teamOfOpponent.gameLifecycleArbitraryObjectiveProgress

                                    board += "${CC.PRI}Hits:"
                                    board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Team: ${CC.PRI}${
                                        teamOfPlayer.gameLifecycleArbitraryObjectiveProgress
                                    }${
                                        if (hitsDiff == 0)
                                            ""
                                        else
                                            if (hitsDiff > 0)
                                                " ${CC.GREEN}(+$hitsDiff)"
                                            else
                                                " ${CC.RED}($hitsDiff)"
                                    }"

                                    board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Enemy: ${CC.RED}${teamOfOpponent.gameLifecycleArbitraryObjectiveProgress}"
                                    appendExtendedOpponentTeamDescription()
                                }

                                board += ""
                                board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Map: ${CC.WHITE}${game.map.displayName}"
                                board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Duration: ${CC.WHITE}${game.getDuration()}"
                            }
                        } else
                        {

                        }
                    }

                    GameState.Completed ->
                    {
                        val report = game.report

                        if (report != null)
                        {
                            fun List<UUID>.toReport(): String
                            {
                                val firstUsername = if (isNotEmpty())
                                {
                                    if (first() in Globals.POSSIBLE_PLAYER_BOT_UNIQUE_IDS)
                                    {
                                        game.robot(first())?.name() ?: "Robot"
                                    }
                                    else
                                    {
                                        game.usernameOf(first())
                                    }
                                } else
                                {
                                    null
                                }

                                return when (size)
                                {
                                    0 -> "N/A"
                                    1 -> firstUsername + CC.GRAY + (Bukkit.getPlayer(first())?.let { " ${CC.D_GRAY}(" + MinecraftReflection.getPing(it) + "ms)" } ?: "")

                                    else -> "$firstUsername's Team"
                                }
                            }

                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Winner:"
                            board += " ${CC.GREEN}${report.winners.toReport()}"
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Loser:"
                            board += " ${CC.RED}${report.losers.toReport()}"
                            board += ""
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Duration: ${CC.WHITE}${game.getDuration()}"
                            board += "${CC.PRI}${Constants.THIN_VERTICAL_LINE} ${CC.SEC}Kit: ${CC.WHITE}${report.kit}"
                        } else
                        {
                            board += "${CC.D_GRAY}Loading report..."
                        }
                    }
                }
            } else
            {
                val scoreboardProxy = game.miniGameLifecycle!!.scoreboard
                when (game.state)
                {
                    GameState.Waiting ->
                    {
                        scoreboardProxy.createWaitingScoreboardFor(player, board)
                    }

                    GameState.Starting ->
                    {
                        scoreboardProxy.createStartingScoreboardFor(player, board)
                    }

                    GameState.Playing ->
                    {
                        scoreboardProxy.createInGameScoreboardFor(player, board)
                    }

                    GameState.Completed ->
                    {
                        scoreboardProxy.createEndingScoreboardFor(player, game.report, board)
                    }
                }
            }
        }

        board += ""
        board += "${CC.DARK_GRAY}${LemonConstants.WEB_LINK}${CC.GRAY}      ${CC.PRI}"
    }

    override fun getTitle(player: Player): String
    {
        player.toHostedWorld()?.apply {
            return generateScoreboardTitle(player)
        }

        val game = GameService.byPlayerOrSpectator(player.uniqueId)
            ?: return "${CC.B_PRI}Duels${
                if ("mipdev" in ServerSync.getLocalGameServer().groups) " ${CC.D_GRAY}(dev)" else ""
            }"

        return game.miniGameLifecycle?.scoreboard?.titleFor(player)
            ?: "${CC.B_PRI}${if (game.isModernDuel()) "Modern Duels" else "Duels"}${
                if ("mipdev" in ServerSync.getLocalGameServer().groups) " ${CC.D_GRAY}(dev)" else ""
            }"
    }
}
