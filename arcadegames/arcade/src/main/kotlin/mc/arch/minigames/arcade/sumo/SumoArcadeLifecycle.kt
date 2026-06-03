package mc.arch.minigames.arcade.sumo

import gg.tropic.practice.expectation.ExpectationService.returnToSpawnItem
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.games.event.PlayerSelectSpawnLocationEvent
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.minigame.*
import gg.tropic.practice.strategies.MarkSpectatorStrategy
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import mc.arch.minigames.arcade.ArcadeTypeMetadata
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.scheduler.Task
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.nametag.NametagHandler
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.visibility.VisibilityHandler
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.lang.AutoCloseable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Subham
 * @since 7/26/25
 */
class SumoArcadeLifecycle(
    override val configuration: ArcadeMiniGameConfiguration,
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    override val typeConfiguration: MiniGameTypeMetadata = ArcadeTypeMetadata,
    override val events: List<MiniGameEvent> = listOf()
) : MiniGameLifecycle<ArcadeMiniGameConfiguration>
{
    val playerResources = ConcurrentHashMap<UUID, SumoPlayerResources>()
    var activelyFightingPlayers: Pair<SumoPlayerResources, SumoPlayerResources>? = null
        private set

    private var matchCountdown = 0
    var isInMatch = false
        private set
    var roundNumber = 1
        private set

    // Per-duel timeout, overridable via private-game settings.
    private val roundTimeoutMs: Long = run {
        val seconds = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.SUMO_ROUND_TIME, ArcadePrivateGameSettings.DEFAULT_SUMO_ROUND_TIME)
            ?: ArcadePrivateGameSettings.DEFAULT_SUMO_ROUND_TIME
        seconds * 1000L
    }

    // Delay between duels in the bracket, overridable via private-game settings.
    private val nextRoundDelayTicks: Long = run {
        val seconds = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.SUMO_NEXT_ROUND_DELAY, ArcadePrivateGameSettings.DEFAULT_SUMO_NEXT_ROUND_DELAY)
            ?: ArcadePrivateGameSettings.DEFAULT_SUMO_NEXT_ROUND_DELAY
        seconds * 20L
    }

    // Simplified thread-safe state management
    private val gameEnded = AtomicBoolean(false)
    private val roundLock = Any() // Lock for round transitions
    @Volatile private var isRoundInProgress = false
    @Volatile private var matchTask: Task? = null

    fun toNonEliminatedPlayers() = playerResources.values
        .filter { !it.eliminated && it.toPlayer() != null }

    fun determineWinner() = toNonEliminatedPlayers().firstOrNull()
    fun hasWinner() = toNonEliminatedPlayers().size == 1

    fun randomlyPairAlivePlayers(): Pair<SumoPlayerResources, SumoPlayerResources>?
    {
        val alivePlayers = toNonEliminatedPlayers()
        if (alivePlayers.size < 2)
        {
            return null
        }

        return alivePlayers.shuffled().take(2)
            .let {
                it.first() to it.last()
            }
    }

    fun toPlayerResources(player: Player) = playerResources
        .getOrPut(player.uniqueId) {
            SumoPlayerResources(
                player = player.uniqueId,
                username = player.name,
                eliminated = false
            )
        }

    override val scoreboard: MiniGameScoreboard = SumoScoreboard(game, configuration, this)
    private val backingTerminable = CompositeTerminable.create()
    /**
     * Directly starts the next round with proper synchronization.
     * This replaces the polling-based scheduler with direct calls.
     */
    private fun startNextRound()
    {
        if (gameEnded.get()) return

        synchronized(roundLock) {
            if (isRoundInProgress) return
            isRoundInProgress = true
        }

        try
        {
            if (hasWinner())
            {
                handleGameWin()
                return
            }

            val nextPair = randomlyPairAlivePlayers()
            if (nextPair != null)
            {
                game.arenaWorld.players.forEach { player ->
                    player.sendMessage("${CC.B_YELLOW}A new round will start in 3 seconds...")
                    player.playSound(player.location, Sound.LEVEL_UP, 1.0f, 1.0f)
                }

                // Configurable delay before starting new round
                Schedulers.sync().runLater({
                    synchronized(roundLock) {
                        isRoundInProgress = false
                    }
                    if (!gameEnded.get())
                    {
                        prepareMatchFor(nextPair)
                    }
                }, nextRoundDelayTicks)
            } else
            {
                // Not enough players but no winner - end game
                gameEnded.set(true)
                synchronized(roundLock) {
                    isRoundInProgress = false
                }
                game.complete(null)
            }
        } catch (e: Exception)
        {
            synchronized(roundLock) {
                isRoundInProgress = false
            }
            throw e
        }
    }

    fun prepareMatchFor(
        matchPlayers: Pair<SumoPlayerResources, SumoPlayerResources>
    )
    {
        if (gameEnded.get()) return

        // Close any stale match task from a prior round. PlayerQuitEvent can route to
        // endCurrentMatch without the task closing itself, which would leak it into the
        // next round and race against the new task on shared matchCountdown/isInMatch.
        matchTask?.closeAndReportException()
        matchTask = null

        activelyFightingPlayers = matchPlayers
        matchCountdown = 5
        isInMatch = false

        val player1 = matchPlayers.first.toPlayer()
        val player2 = matchPlayers.second.toPlayer()

        if (player1 == null || player2 == null)
        {
            // One of the players left, eliminate the missing one and request new match
            if (player1 == null) matchPlayers.first.eliminated = true
            if (player2 == null) matchPlayers.second.eliminated = true

            activelyFightingPlayers = null
            requestNewRound()
            return
        }

        val spawn1 = game.map
            .findSpawnLocationMatchingTeam(TeamIdentifier.A)!!
            .toLocation(game.arenaWorld)

        val spawn2 = game.map
            .findSpawnLocationMatchingTeam(TeamIdentifier.B)!!
            .toLocation(game.arenaWorld)

        val minimumDeathY = spawn1.y - 6
        GameService.lightSpectatorPlayers += player1.uniqueId
        GameService.lightSpectatorPlayers += player2.uniqueId
        GameService.spectatorPlayers -= player1.uniqueId
        GameService.spectatorPlayers -= player2.uniqueId

        listOf(player1, player2).forEach { fighter ->
            NametagHandler.reloadPlayer(fighter)
            VisibilityHandler.update(fighter)
        }

        player1.teleport(spawn1)
        player2.teleport(spawn2)

        listOf(player1, player2).forEach { player ->
            player.inventory.clear()
            player.health = 20.0
            player.foodLevel = 20
            player.allowFlight = false
            player.isFlying = false
        }

        // Announce the match
        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.YELLOW}Round $roundNumber: ${CC.GREEN}${player1.name} ${CC.YELLOW}vs ${CC.RED}${player2.name}")
            player.playSound(player.location, Sound.LEVEL_UP, 1.0f, 1.0f)
        }

        // Track when the match actually starts for timeout
        var matchStartTime = 0L

        matchTask = Schedulers
            .sync()
            .runRepeating({ task ->
                if (gameEnded.get() || activelyFightingPlayers !== matchPlayers)
                {
                    task.closeAndReportException()
                    return@runRepeating
                }

                if (matchCountdown > 0)
                {
                    // Countdown phase
                    game.arenaWorld.players.forEach { player ->
                        player.sendMessage("${CC.GREEN}Round starts in ${CC.WHITE}${matchCountdown}s${CC.GREEN}...")
                        player.playSound(
                            player.location,
                            Sound.NOTE_STICKS,
                            1.0f,
                            1.0f
                        )
                    }
                    matchCountdown--
                } else if (!isInMatch)
                {
                    // Start the match
                    isInMatch = true
                    matchStartTime = System.currentTimeMillis()

                    GameService.lightSpectatorPlayers -= player1.uniqueId
                    GameService.lightSpectatorPlayers -= player2.uniqueId

                    game.arenaWorld.players.forEach { player ->
                        player.sendMessage("${CC.B_GREEN}Round started!")
                        player.playSound(player.location, Sound.FIREWORK_BLAST, 1.0f, 2.0f)
                    }
                } else
                {
                    // Check for match end conditions
                    val currentPlayer1 = matchPlayers.first.toPlayer()
                    val currentPlayer2 = matchPlayers.second.toPlayer()

                    if (currentPlayer1 == null || currentPlayer2 == null)
                    {
                        // Someone left
                        if (currentPlayer1 == null) eliminatePlayer(matchPlayers.first, "left the game")
                        if (currentPlayer2 == null) eliminatePlayer(matchPlayers.second, "left the game")
                        task.closeAndReportException()
                        endCurrentMatch()
                        return@runRepeating
                    }

                    if (currentPlayer1.location.y < minimumDeathY)
                    {
                        eliminatePlayer(matchPlayers.first, "fell off the platform")
                        SumoRewards.awardKnockoff(matchPlayers.second)
                        task.closeAndReportException()
                        endCurrentMatch()
                        return@runRepeating
                    }

                    if (currentPlayer2.location.y < minimumDeathY)
                    {
                        eliminatePlayer(matchPlayers.second, "fell off the platform")
                        SumoRewards.awardKnockoff(matchPlayers.first)
                        task.closeAndReportException()
                        endCurrentMatch()
                        return@runRepeating
                    }

                    // Check for timeout (1 minute)
                    if (matchStartTime > 0 && System.currentTimeMillis() - matchStartTime > roundTimeoutMs)
                    {
                        // Ensure players are unfrozen
                        GameService.lightSpectatorPlayers -= matchPlayers.first.player
                        GameService.lightSpectatorPlayers -= matchPlayers.second.player

                        game.arenaWorld.players.forEach { player ->
                            player.sendMessage("${CC.B_YELLOW}Round timed out! Neither player was eliminated.")
                            player.playSound(player.location, Sound.VILLAGER_NO, 1.0f, 1.0f)
                        }
                        task.closeAndReportException()
                        endCurrentMatch()
                        return@runRepeating
                    }
                }
            }, 0L, 20L)
            .also { it.bindWith(this) }
    }

    private fun eliminatePlayer(playerResource: SumoPlayerResources, reason: String)
    {
        // Prevent double elimination
        if (playerResource.eliminated) return

        playerResource.eliminated = true
        val eliminated = playerResource.toPlayer()
            ?: return

        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.RED}${playerResource.username} ${CC.GRAY}$reason!")
            player.playSound(eliminated.location, Sound.AMBIENCE_THUNDER, 1.0f, 1.0f)
        }

        // Mark as spectator
        MarkSpectatorStrategy.markSpectator(
            player = eliminated,
            world = game.arenaWorld,
            shouldAnnounce = false
        )

        eliminated.teleport(game.spectatorLocation())
        SumoRewards.awardEliminated(eliminated)
    }

    /**
     * Thread-safe method to request a new round
     */
    private fun requestNewRound()
    {
        if (!gameEnded.get())
        {
            startNextRound()
        }
    }

    private fun endCurrentMatch()
    {
        if (gameEnded.get()) return

        activelyFightingPlayers?.let { resources ->
            listOfNotNull(resources.first.toPlayer(), resources.second.toPlayer())
                .forEach { player ->
                    MarkSpectatorStrategy.markSpectator(
                        player = player,
                        world = game.arenaWorld,
                        shouldAnnounce = false
                    )

                    player.teleport(game.spectatorLocation())
                }
        }

        game.arenaWorld.players.forEach {
            GameService.lightSpectatorPlayers -= it.uniqueId
        }

        activelyFightingPlayers = null
        isInMatch = false
        roundNumber++

        // Directly start next round
        startNextRound()
    }

    /**
     * Handle game win scenario
     */
    private fun handleGameWin()
    {
        if (gameEnded.getAndSet(true)) return // Prevent multiple win processing

        val winner = determineWinner()!!
        val winnerPlayer = winner.toPlayer()!!

        matchTask?.closeAndReportException()
        matchTask = null

        SumoRewards.awardWinner(winner)

        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.B_GOLD}${winnerPlayer.name} ${CC.GOLD}won Sumo!")
            player.playSound(player.location, Sound.FIREWORK_LAUNCH, 1.0f, 1.0f)
        }

        var times = 0L
        Schedulers
            .async()
            .runRepeating({ task ->
                if (times >= 3)
                {
                    task.closeAndReportException()
                    return@runRepeating
                }

                times += 1
                game.arenaWorld.players.forEach { player ->
                    player.playSound(player.location, Sound.FIREWORK_LAUNCH, 1.0f, 1.0f)
                }
            }, 0L, 20L)
            .bindWith(this)

        // End the game
        Schedulers
            .async()
            .runLater({
                game.complete(null)
            }, SumoConstants.WIN_CELEBRATION_DELAY_TICKS)
            .bindWith(this)
    }

    override fun configure()
    {
        game.shouldShowAllPlayers = false
        game.shouldKeepCentralChat = true
        game.shouldAllowFriendlyFire = true

        Events
            .subscribe(EntityDamageEvent::class.java)
            .filter {
                it.cause == EntityDamageEvent.DamageCause.FALL &&
                    it.entity is Player &&
                    GameService.byPlayer((it.entity as Player)) == game
            }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game == game }
            .handler { event ->
                if (game.state == GameState.Waiting || game.state == GameState.Starting)
                {
                    event.player.inventory.setItem(8, returnToSpawnItem)
                    event.player.updateInventory()
                    ArcadeBroadcastTrigger.giveItemIfPermitted(event.player, slot = 0)

                    toPlayerResources(event.player)
                    return@handler
                }

                MarkSpectatorStrategy.markSpectator(
                    player = event.player,
                    world = game.arenaWorld,
                    shouldAnnounce = false
                )

                playerResources[event.player.uniqueId] = SumoPlayerResources(
                    player = event.player.uniqueId,
                    username = event.player.name,
                    eliminated = true
                )
            }
            .bindWith(this)

        Events
            .subscribe(PlayerSelectSpawnLocationEvent::class.java)
            .filter { it.game == game }
            .handler {
                it.location = game.spectatorLocation()
            }
            .bindWith(this)

        Events
            .subscribe(PlayerQuitEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { event ->
                val playerResource = playerResources.remove(event.player.uniqueId)
                if (
                    playerResource != null &&
                    !playerResource.eliminated &&
                    !(game.state(GameState.Waiting) || game.state(GameState.Starting))
                )
                {
                    eliminatePlayer(playerResource, "left the game")

                    // If this was one of the fighting players, end the match
                    activelyFightingPlayers?.let { fighting ->
                        if (fighting.first.player == event.player.uniqueId ||
                            fighting.second.player == event.player.uniqueId
                        )
                        {
                            endCurrentMatch()
                        }
                    }
                }
            }
            .bindWith(this)

        Events
            .subscribe(GameStartEvent::class.java)
            .filter { it.game == game }
            .handler { event ->
                game.arenaWorld.players.forEach {
                    MarkSpectatorStrategy.markSpectator(
                        player = it,
                        world = game.arenaWorld,
                        shouldAnnounce = false
                    )
                }

                Schedulers.sync().runLater({
                    if (!gameEnded.get())
                    {
                        startNextRound()
                    }
                }, SumoConstants.FIRST_MATCH_DELAY_TICKS)
            }
            .bindWith(this)
    }

    override fun close() = backingTerminable.close()

    override fun with(autoCloseable: AutoCloseable?): CompositeTerminable? =
        backingTerminable.with(autoCloseable)

    override fun cleanup()
    {
        gameEnded.set(true)
        backingTerminable.cleanup()
    }

}
