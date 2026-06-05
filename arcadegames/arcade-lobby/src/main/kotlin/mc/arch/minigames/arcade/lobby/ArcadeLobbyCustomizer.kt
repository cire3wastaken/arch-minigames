package mc.arch.minigames.arcade.lobby

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.livePlayerCount
import gg.tropic.practice.metadata.SystemMetadataService
import gg.tropic.practice.minigame.MiniGameTypeProvider
import gg.tropic.practice.minigame.MinigameLobby
import gg.tropic.practice.minigame.MinigameLobbyCustomizer
import gg.tropic.practice.minigame.MinigameLobbyScoreboardProvider
import gg.tropic.practice.player.LobbyPlayer
import mc.arch.minigame.miniwalls.MiniWallsGameType
import mc.arch.minigames.arcade.ArcadeTypeMetadata
import mc.arch.minigames.arcade.lobby.extension.ArcadeGameExtensionRegistry
import mc.arch.minigames.arcade.lobby.extension.ArcadeGlobalCompetitiveCustomizer
import mc.arch.minigames.arcade.lobby.menu.ArcadeCatalog
import mc.arch.minigames.pof.PofGameType
import mc.arch.minigames.hungergames.HungerGamesTypeMetadata
import mc.arch.minigames.skywars.SkyWarsTypeMetadata
import mc.arch.minigames.arcade.lobby.menu.ArcadeGameSelectorMenu
import mc.arch.minigames.arcade.lobby.menu.ArcadeManageMenu
import me.lucko.helper.Events
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent

@Service
object ArcadeLobbyCustomizer : MinigameLobbyCustomizer, MinigameLobbyScoreboardProvider, MiniGameTypeProvider
{
    override val id: String = "arcade"

    override fun mainMenuProvider(player: Player)
    {
        ArcadeManageMenu().openMenu(player)
    }

    override fun playProvider(player: Player)
    {
        ArcadeGameSelectorMenu().openMenu(player)
    }

    @Configure
    fun configure()
    {
        MinigameLobby.competitiveResolver = { _ ->
            ArcadeGlobalCompetitiveCustomizer
        }
         MinigameLobby.holographicStatsEnabled = { true }

        ArcadeTypeMetadata.registerExternalModes("sg", HungerGamesTypeMetadata.gameModes)
        ArcadeTypeMetadata.registerExternalModes("mw", MiniWallsGameType.gameModes)
        ArcadeTypeMetadata.registerExternalModes("sw", SkyWarsTypeMetadata.gameModes)
        ArcadeTypeMetadata.registerExternalModes("pof", PofGameType.gameModes)

        MinigameLobby.customize(this, this)

        Events
            .subscribe(PlayerJoinEvent::class.java)
            .handler { event ->
                ArcadeGameExtensionRegistry
                    .all()
                    .forEach { runCatching { it.onLobbyJoin(event.player) } }
            }
    }

    override fun scoreboard() = this

    override fun provideTitle() = "${CC.BD_PURPLE}ARCADE"

    private val arcadeTypeIds = ArcadeCatalog.cards.map { it.internalId }.toSet()

    override fun provideIdleLines(
        player: Player,
        lobbyProfile: LobbyPlayer
    ): List<String>
    {
        val inLobby = ArcadeTypeMetadata.totalPlayersInLobby()
        val playing = SystemMetadataService
            .allGames()
            .filter { it.miniGameType in arcadeTypeIds }
            .sumOf { it.livePlayerCount() }

        return listOf(
            "${CC.D_PURPLE}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Online: ${CC.WHITE}${Numbers.format(inLobby + playing)}",
            "${CC.D_PURPLE}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}In-game: ${CC.WHITE}${Numbers.format(playing)}"
        )
    }

    override fun provide() = ArcadeTypeMetadata
}
