package mc.arch.minigames.pof

import com.cryptomorin.xseries.XSound
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.games.event.PlayerSelectSpawnLocationEvent
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameEvent
import gg.tropic.practice.minigame.MiniGameLifecycle
import gg.tropic.practice.minigame.MiniGameScoreboard
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import gg.tropic.practice.minigame.event.PlayerMiniGameSpectateEvent
import gg.tropic.practice.minigame.event.functionality.MiniGamePlayerDeathEvent
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.strategies.MarkSpectatorStrategy
import mc.arch.minigame.pof.PofGameConfiguration
import mc.arch.minigame.pof.PofGameType
import mc.arch.minigames.pof.loadout.LegacyPofLootTable
import mc.arch.minigames.pof.loadout.ModernPofLootTable
import mc.arch.minigames.pof.loadout.PofLoadout
import mc.arch.minigames.pof.loadout.PofLootTable
import mc.arch.minigames.pof.rewards.PofRewards
import mc.arch.minigames.pof.state.PofPlayerResources
import mc.arch.minigames.pof.state.PofTeamResources
import me.lucko.helper.Events
import me.lucko.helper.Helper
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.nametag.NametagHandler
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.visibility.VisibilityHandler
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.metadata.FixedMetadataValue
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PofGameLifecycle(
    override val configuration: PofGameConfiguration,
    override val game: AbstractMiniGameGameImpl<PofGameConfiguration>,
    override val typeConfiguration: MiniGameTypeMetadata = PofGameType,
    override val events: List<MiniGameEvent> = listOf()
) : MiniGameLifecycle<PofGameConfiguration>, CompositeTerminable by CompositeTerminable.create()
{
    private val playerResources = ConcurrentHashMap<UUID, PofPlayerResources>()
    private val teamResources = ConcurrentHashMap<TeamIdentifier, PofTeamResources>()
    private val lastDamager = ConcurrentHashMap<UUID, UUID>()
    private val gameEnded = AtomicBoolean(false)
    private val deathmatchActive = AtomicBoolean(false)
    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val assignedSpawns = ConcurrentHashMap<UUID, Location>()
    private var buildCeilingY: Int? = null
    private var voidKillY: Int? = null
    private var gameStartTime = 0L

    private val isTeamMode: Boolean get() = configuration.mode.teamSize > 1
    private val mode get() = PofGameType.gameModes.values.first { it.mode === configuration.mode }

    private val lootTable: PofLootTable =
        if (configuration.mode.providerVersion == MiniProviderVersion.LEGACY) LegacyPofLootTable
        else ModernPofLootTable

    private fun isActive(): Boolean =
        !gameEnded.get() &&
            game.state(GameState.Playing) &&
            frozenPlayers.isEmpty()

    private fun activeParticipant(player: Player): PofPlayerResources?
    {
        if (GameService.byPlayer(player) != game) return null
        val resources = playerResources[player.uniqueId] ?: return null
        if (resources.spectator) return null
        return resources
    }

    fun remainingMs(): Long
    {
        if (gameStartTime == 0L) return configuration.gameTimeoutMs
        return (configuration.gameTimeoutMs - (System.currentTimeMillis() - gameStartTime)).coerceAtLeast(0L)
    }

    override val scoreboard: MiniGameScoreboard = PofScoreboard(
        game = game,
        configuration = configuration,
        resourcesProvider = { playerResources.values },
        remainingMs = ::remainingMs,
        deathmatchActive = { deathmatchActive.get() }
    )

    private val audiences = BukkitAudiences.create(Helper.hostPlugin()).also(this::with)

    private fun toPlayerResources(player: Player): PofPlayerResources
    {
        return playerResources.getOrPut(player.uniqueId) {
            PofPlayerResources(
                player = player.uniqueId,
                username = player.name,
                team = teamResources.values.firstOrNull { player.uniqueId in it.members }?.identifier
            )
        }
    }

    private fun fallbackSpawn(): Location? =
        game.map.findSpawnLocationMatchingTeam(TeamIdentifier.A)
            ?.toLocation(game.arenaWorld)

    private fun assignSpawnsTo(participants: List<PofPlayerResources>)
    {
        assignedSpawns.clear()
        if (participants.isEmpty()) return

        val spawns = game.map.findSpawnLocations()
            .filter { it.id != "spec" }
            .map { it.position.toLocation(game.arenaWorld) }
            .shuffled()
            .toMutableList()

        if (isTeamMode)
        {
            val groupedByTeam = participants.groupBy { it.team ?: TeamIdentifier.A }
            groupedByTeam.entries.forEachIndexed { teamIndex, (_, members) ->
                val location = if (teamIndex < spawns.size)
                {
                    spawns[teamIndex]
                } else
                {
                    spawns.getOrNull(teamIndex % spawns.size) ?: fallbackSpawn() ?: return@forEachIndexed
                }
                members.forEach { resources -> assignedSpawns[resources.player] = location }
            }
        } else
        {
            participants.forEachIndexed { index, resources ->
                val location = if (index < spawns.size)
                {
                    spawns[index]
                } else
                {
                    spawns.getOrNull(index % spawns.size) ?: fallbackSpawn() ?: return@forEachIndexed
                }
                assignedSpawns[resources.player] = location
            }
        }
    }

    private fun spawnPlayer(resources: PofPlayerResources)
    {
        if (gameEnded.get()) return

        val player = resources.toPlayer() ?: return
        if (resources.spectator) return

        GameService.spectatorPlayers -= player.uniqueId
        GameService.lightSpectatorPlayers -= player.uniqueId

        player.allowFlight = false
        player.isFlying = false
        player.gameMode = GameMode.SURVIVAL

        (assignedSpawns[resources.player] ?: fallbackSpawn())?.let(player::teleport)
        PofLoadout.apply(player)
        resources.lastSpawnTime = System.currentTimeMillis()

        game.arenaWorld.players.forEach { other ->
            VisibilityHandler.update(other)
            NametagHandler.reloadPlayer(player, other)
        }
    }

    private fun refreshSpectatorVisibility(player: Player)
    {
        VisibilityHandler.update(player)
        game.arenaWorld.players.forEach { other ->
            if (other.uniqueId != player.uniqueId) VisibilityHandler.update(other)
        }
    }

    private fun isInSpawnProtection(resources: PofPlayerResources): Boolean
    {
        if (resources.lastSpawnTime == 0L) return false
        return System.currentTimeMillis() - resources.lastSpawnTime < configuration.spawnProtectionMs
    }

    private fun aliveResources() = playerResources.values
        .filter { !it.spectator && it.toPlayer() != null }

    private fun aliveTeamCount(): Int
    {
        val alive = aliveResources()
        return if (isTeamMode)
        {
            alive.mapNotNull { it.team }.toSet().size
        } else
        {
            alive.size
        }
    }

    private fun isSameTeam(a: UUID, b: UUID): Boolean
    {
        if (!isTeamMode) return false
        val teamA = playerResources[a]?.team ?: return false
        val teamB = playerResources[b]?.team ?: return false
        return teamA == teamB
    }

    private fun markEliminated(victim: PofPlayerResources, killerId: UUID?, cause: String)
    {
        if (victim.spectator) return

        if (!game.state(GameState.Playing) || frozenPlayers.isNotEmpty())
        {
            playerResources.remove(victim.player)
            lastDamager.remove(victim.player)
            game.expectationModel.players -= victim.player
            game.getNullableTeamOfID(victim.player)?.players?.remove(victim.player)
            return
        }

        victim.spectator = true
        victim.deaths += 1
        victim.survivedUntil = System.currentTimeMillis()

        val killerResources = killerId
            ?.let(playerResources::get)
            ?.takeUnless { it.spectator || it.player == victim.player || isSameTeam(it.player, victim.player) }

        if (killerResources != null)
        {
            killerResources.kills += 1
        }

        val victimPlayer = victim.toPlayer()
        if (victimPlayer != null)
        {
            victimPlayer.inventory.clear()
            victimPlayer.inventory.armorContents = arrayOfNulls(4)
            victimPlayer.health = victimPlayer.maxHealth
            victimPlayer.foodLevel = 20
            victimPlayer.fireTicks = 0
            victimPlayer.activePotionEffects.forEach { victimPlayer.removePotionEffect(it.type) }
            victimPlayer.teleport(game.spectatorLocation())
            XSound.ENTITY_PLAYER_HURT.play(victimPlayer)

            MarkSpectatorStrategy.markSpectator(
                player = victimPlayer,
                world = game.arenaWorld,
                shouldAnnounce = false,
                shouldAddSpectatorItem = false
            )

            victimPlayer.gameMode = GameMode.SPECTATOR
            refreshSpectatorVisibility(victimPlayer)
        }

        val victimName = victimPlayer?.name ?: victim.username
        val killerPlayer = killerResources?.toPlayer()
        val message = if (killerPlayer != null && killerResources.player != victim.player)
        {
            "${CC.RED}$victimName ${CC.GRAY}was eliminated by ${CC.GREEN}${killerPlayer.name}"
        } else
        {
            "${CC.RED}$victimName ${CC.GRAY}$cause"
        }
        game.arenaWorld.players.forEach { it.sendMessage(message) }
        game.arenaWorld.players.forEach { XSound.BLOCK_NOTE_BLOCK_PLING.play(it) }

        lastDamager.remove(victim.player)
        checkWinCondition()
    }

    private fun checkWinCondition()
    {
        if (gameEnded.get()) return
        if (aliveTeamCount() <= 1)
        {
            val survivors = aliveResources()
            handleGameEnd(survivors, timeoutReached = false)
        }
    }

    private fun handleGameEnd(survivors: Collection<PofPlayerResources>, timeoutReached: Boolean)
    {
        if (gameEnded.getAndSet(true)) return

        val winnerIds = survivors.map { it.player }.toSet()
        if (survivors.isNotEmpty())
        {
            PofRewards.awardWinners(survivors, mode)
        }
        PofRewards.awardParticipants(playerResources.values, winnerIds, mode)

        val winnerLabel = when
        {
            survivors.isEmpty() -> null
            isTeamMode -> survivors.joinToString(separator = " & ") { it.toPlayer()?.name ?: it.username }
            else -> survivors.first().toPlayer()?.name ?: survivors.first().username
        }
        game.arenaWorld.players.forEach { player ->
            if (winnerLabel != null)
            {
                val prefix = if (timeoutReached) "${CC.B_YELLOW}Time's up! " else ""
                player.sendMessage("$prefix${CC.B_GOLD}$winnerLabel ${CC.GOLD}won Pillar of Fortune!")
            } else
            {
                player.sendMessage("${CC.GOLD}Pillar of Fortune ended with no survivors.")
            }
            XSound.ENTITY_FIREWORK_ROCKET_LAUNCH.play(player)
        }

        var times = 0L
        Schedulers.sync()
            .runRepeating({ task ->
                if (times >= 3)
                {
                    task.close()
                    return@runRepeating
                }
                times += 1
                game.arenaWorld.players.forEach { XSound.ENTITY_FIREWORK_ROCKET_LAUNCH.play(it) }
            }, 0L, 20L)
            .bindWith(this)

        Schedulers.sync()
            .runLater({ game.complete(null) }, 100L)
            .bindWith(this)
    }

    private fun projectileShooterIfPlayer(damager: org.bukkit.entity.Entity): Player?
    {
        if (damager !is Projectile) return null
        val shooter = damager.shooter as? Player ?: return null
        val resources = playerResources[shooter.uniqueId] ?: return null
        if (resources.spectator) return null
        return shooter
    }

    override fun configure()
    {
        game.shouldShowAllPlayers = false
        game.shouldKeepCentralChat = false
        game.shouldAllowFriendlyFire = !isTeamMode
        game.shouldAllowCrafting = true
        game.isMinIndependent = true
        game.kit.features.getOrPut(FeatureFlag.PlaceBlocks) { mutableMapOf() }
        game.kit.features.getOrPut(FeatureFlag.BreakPlacedBlocks) { mutableMapOf() }
        game.kit.features.remove(FeatureFlag.BuildLimit)
        game.kit.features.remove(FeatureFlag.DoNotTakeDamage)

        teamResources.clear()
        game.teams.forEach { gameTeam ->
            teamResources[gameTeam.teamIdentifier] = PofTeamResources(gameTeam, gameTeam.teamIdentifier)
        }

        Events
            .subscribe(FoodLevelChangeEvent::class.java)
            .filter { it.entity is Player && GameService.byPlayer(it.entity as Player) == game }
            .handler { event ->
                if (deathmatchActive.get()) return@handler
                event.isCancelled = true
                val player = event.entity as Player
                player.foodLevel = 20
                player.saturation = 20f
            }
            .bindWith(this)

        Events
            .subscribe(EntityDamageByEntityEvent::class.java, EventPriority.HIGHEST)
            .filter { it.entity is Player && GameService.byPlayer(it.entity as Player) == game }
            .handler(::handleEntityDamageByEntity)
            .bindWith(this)

        Events
            .subscribe(EntityDamageEvent::class.java)
            .filter { it.entity is Player && GameService.byPlayer(it.entity as Player) == game }
            .filter { it.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK && it.cause != EntityDamageEvent.DamageCause.PROJECTILE }
            .handler(::handleEnvironmentalDamage)
            .bindWith(this)

        Events
            .subscribe(MiniGamePlayerDeathEvent::class.java)
            .filter { it.game === this }
            .handler(::handlePlayerDeath)
            .bindWith(this)

        Events
            .subscribe(BlockPlaceEvent::class.java, EventPriority.HIGHEST)
            .handler { event ->
                val resources = activeParticipant(event.player) ?: return@handler
                if (!isActive())
                {
                    event.isCancelled = true
                    return@handler
                }

                val ceiling = buildCeilingY
                if (ceiling != null && event.blockPlaced.y > ceiling)
                {
                    event.isCancelled = true
                    event.player.sendMessage("${CC.RED}You cannot build above ${configuration.buildHeightAboveSpawn} blocks!")
                    return@handler
                }

                event.isCancelled = false
                resources.blocksPlaced += 1
                if (!event.blockPlaced.hasMetadata("placed"))
                {
                    event.blockPlaced.setMetadata(
                        "placed",
                        FixedMetadataValue(Helper.hostPlugin(), event.player.uniqueId)
                    )
                }
            }
            .bindWith(this)

        Events
            .subscribe(BlockBreakEvent::class.java, EventPriority.HIGHEST)
            .handler { event ->
                if (activeParticipant(event.player) == null) return@handler
                if (!isActive())
                {
                    event.isCancelled = true
                    return@handler
                }
                event.isCancelled = !event.block.hasMetadata("placed")
            }
            .bindWith(this)

        Events
            .subscribe(PlayerPickupItemEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { event ->
                val resources = activeParticipant(event.player) ?: return@handler
                resources.lootPickedUp += event.item.itemStack.amount
            }
            .bindWith(this)

        Events
            .subscribe(PlayerDropItemEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .filter { game.state(GameState.Waiting) || game.state(GameState.Starting) || it.player.uniqueId in frozenPlayers }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerMoveEvent::class.java)
            .filter { it.player.uniqueId in frozenPlayers }
            .handler { event ->
                val from = event.from
                val to = event.to ?: return@handler
                if (from.blockX != to.blockX || from.blockZ != to.blockZ || from.y != to.y)
                {
                    event.setTo(Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch))
                }
            }
            .bindWith(this)

        Schedulers.sync().runRepeating({ _ ->
            val threshold = voidKillY ?: return@runRepeating
            aliveResources().forEach { resources ->
                val player = resources.toPlayer() ?: return@forEach
                if (player.location.y >= threshold) return@forEach
                markEliminated(resources, lastDamager[resources.player], cause = "fell into the void.")
            }
        }, 1L, 1L).bindWith(this)

        Events
            .subscribe(PlayerInteractEvent::class.java, EventPriority.LOWEST)
            .filter { it.player.uniqueId in frozenPlayers }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game == game }
            .handler(::handlePlayerJoin)
            .bindWith(this)

        Events
            .subscribe(PlayerMiniGameSpectateEvent::class.java)
            .filter { it.game === this }
            .handler { event ->
                event.player.gameMode = GameMode.SPECTATOR
                refreshSpectatorVisibility(event.player)
                playerResources.putIfAbsent(
                    event.player.uniqueId,
                    PofPlayerResources(
                        player = event.player.uniqueId,
                        username = event.player.name,
                        spectator = true
                    )
                )
            }
            .bindWith(this)

        Events
            .subscribe(PlayerSelectSpawnLocationEvent::class.java)
            .filter { it.game == game }
            .handler { it.location = game.spectatorLocation() }
            .bindWith(this)

        Events
            .subscribe(PlayerQuitEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { event ->
                val resources = playerResources[event.player.uniqueId]
                if (resources != null && !resources.spectator && !gameEnded.get())
                {
                    markEliminated(resources, lastDamager[resources.player], cause = "disconnected.")
                } else
                {
                    playerResources.remove(event.player.uniqueId)
                }
                lastDamager.remove(event.player.uniqueId)
            }
            .bindWith(this)

        Events
            .subscribe(GameStartEvent::class.java)
            .filter { it.game == game }
            .handler(::handleGameStart)
            .bindWith(this)
    }

    private fun handleEntityDamageByEntity(event: EntityDamageByEntityEvent)
    {
        if (!isActive())
        {
            event.isCancelled = true
            return
        }

        val victim = event.entity as Player
        val victimResources = activeParticipant(victim) ?: run {
            event.isCancelled = true
            return
        }

        val attacker = projectileShooterIfPlayer(event.damager) ?: (event.damager as? Player)
        if (attacker == null) return

        if (attacker.uniqueId == victim.uniqueId || activeParticipant(attacker) == null)
        {
            event.isCancelled = true
            return
        }

        if (isSameTeam(attacker.uniqueId, victim.uniqueId))
        {
            event.isCancelled = true
            return
        }

        val attackerResources = playerResources[attacker.uniqueId]!!
        if (isInSpawnProtection(victimResources))
        {
            event.isCancelled = true
            attacker.sendMessage("${CC.RED}That player just spawned in!")
            return
        }
        if (isInSpawnProtection(attackerResources))
        {
            event.isCancelled = true
            return
        }

        lastDamager[victim.uniqueId] = attacker.uniqueId

        if (victim.health - event.finalDamage <= 0.0)
        {
            event.isCancelled = true
            markEliminated(victimResources, attacker.uniqueId, cause = "died.")
            return
        }

        event.isCancelled = false
    }

    private fun handleEnvironmentalDamage(event: EntityDamageEvent)
    {
        if (gameEnded.get())
        {
            event.isCancelled = true
            return
        }

        val victim = event.entity as Player
        val victimResources = playerResources[victim.uniqueId] ?: return
        if (victimResources.spectator)
        {
            event.isCancelled = true
            return
        }

        if (isActive() && victim.health - event.finalDamage <= 0.0)
        {
            event.isCancelled = true
            val cause = when (event.cause)
            {
                EntityDamageEvent.DamageCause.FALL -> "fell off their pillar."
                EntityDamageEvent.DamageCause.VOID -> "fell into the void."
                EntityDamageEvent.DamageCause.FIRE,
                EntityDamageEvent.DamageCause.FIRE_TICK,
                EntityDamageEvent.DamageCause.LAVA -> "burned to death."
                EntityDamageEvent.DamageCause.DROWNING -> "drowned."
                else -> "died."
            }
            markEliminated(victimResources, lastDamager[victim.uniqueId], cause = cause)
            return
        }

        if (victim.uniqueId in frozenPlayers)
        {
            event.isCancelled = true
            return
        }

        if (isInSpawnProtection(victimResources))
        {
            event.isCancelled = true
        }
    }

    private fun handlePlayerDeath(event: MiniGamePlayerDeathEvent)
    {
        val victim = event.player
        val victimResources = playerResources[victim.uniqueId] ?: return
        event.drops.clear()

        val killerId = (event.killer as? Player)?.uniqueId
            ?: lastDamager[victim.uniqueId]

        val cause = when (victim.lastDamageCause?.cause)
        {
            EntityDamageEvent.DamageCause.FALL -> "fell off their pillar."
            EntityDamageEvent.DamageCause.VOID -> "fell into the void."
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA -> "burned to death."
            EntityDamageEvent.DamageCause.DROWNING -> "drowned."
            else -> "died."
        }

        markEliminated(victimResources, killerId, cause)
    }

    private fun handlePlayerJoin(event: PlayerJoinGameEvent)
    {
        if (game.state == GameState.Waiting || game.state == GameState.Starting)
        {
            toPlayerResources(event.player)
            return
        }

        MarkSpectatorStrategy.markSpectator(
            player = event.player,
            world = game.arenaWorld,
            shouldAnnounce = false
        )

        event.player.gameMode = GameMode.SPECTATOR
        refreshSpectatorVisibility(event.player)

        playerResources[event.player.uniqueId] = PofPlayerResources(
            player = event.player.uniqueId,
            username = event.player.name,
            spectator = true
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGameStart(event: GameStartEvent)
    {
        val participants = game.arenaWorld.players
            .map(::toPlayerResources)
            .filter { !it.spectator }
        assignSpawnsTo(participants)

        val spawnY = assignedSpawns.values.maxOfOrNull { it.blockY }
            ?: fallbackSpawn()?.blockY
        if (spawnY != null)
        {
            buildCeilingY = spawnY + configuration.buildHeightAboveSpawn
        }

        voidKillY = game.map.findMapLevelMinMax()
            .minByOrNull { it.yAxis }
            ?.yAxis

        game.arenaWorld.setSpawnFlags(true, true)
        game.voidDamageMin = null

        game.arenaWorld.players.forEach { player ->
            val resources = toPlayerResources(player)
            if (resources.spectator) return@forEach
            spawnPlayer(resources)
            frozenPlayers += player.uniqueId
        }

        runFreezeCountdownThenBegin()
    }

    private fun runFreezeCountdownThenBegin()
    {
        val secondsRemaining = AtomicInteger(configuration.freezeCountdownSeconds)
        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                frozenPlayers.clear()
                task.close()
                return@runRepeating
            }

            val remaining = secondsRemaining.getAndDecrement()
            if (remaining <= 0)
            {
                beginRound()
                task.close()
                return@runRepeating
            }

            val message = "${CC.AQUA}Unfreezing in ${CC.WHITE}${remaining}s${CC.AQUA}..."
            game.arenaWorld.players.forEach { it.sendMessage(message) }
        }, 0L, 20L).bindWith(this)
    }

    private fun beginRound()
    {
        gameStartTime = System.currentTimeMillis()

        frozenPlayers.toList().forEach { uuid ->
            val resources = playerResources[uuid] ?: return@forEach
            val player = resources.toPlayer() ?: return@forEach
            if (resources.spectator) return@forEach
            PofLoadout.apply(player)
            player.gameMode = GameMode.SURVIVAL
            GameService.spectatorPlayers -= player.uniqueId
            GameService.lightSpectatorPlayers -= player.uniqueId
            resources.lastSpawnTime = System.currentTimeMillis()
            audiences.player(player).sendActionBar(Component.text("Begin!", NamedTextColor.GREEN))
            XSound.ENTITY_PLAYER_LEVELUP.play(player)
        }
        frozenPlayers.clear()

        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.B_AQUA}Pillar of Fortune has started! ${CC.GRAY}Random loot drops every ${CC.WHITE}5${CC.GRAY}s — last one standing wins!")
        }

        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }

            val rareUnlocked = System.currentTimeMillis() - gameStartTime >= configuration.rareUnlockMs
            aliveResources().forEach { resources ->
                val player = resources.toPlayer() ?: return@forEach
                val item = lootTable.roll(rareUnlocked)
                val leftover = player.inventory.addItem(item)
                leftover.values.forEach { drop ->
                    player.world.dropItemNaturally(player.location, drop)
                }
            }
        }, configuration.lootIntervalTicks, configuration.lootIntervalTicks).bindWith(this)

        val announcedMilestones = ConcurrentHashMap.newKeySet<Long>()
        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }

            val elapsed = System.currentTimeMillis() - gameStartTime
            val remaining = configuration.gameTimeoutMs - elapsed

            if (remaining <= 0L)
            {
                startDeathmatch()
                task.close()
                return@runRepeating
            }

            val remainingSec = remaining / 1000
            val milestone = when
            {
                remainingSec in 1..10 -> remainingSec
                remainingSec in 11..30 && remainingSec / 30 * 30 == remainingSec -> remainingSec
                remainingSec == 60L -> 60L
                remainingSec == 300L -> 300L
                else -> null
            }
            if (milestone != null && announcedMilestones.add(milestone))
            {
                val text = when
                {
                    milestone >= 60L -> "${milestone / 60} minute${if (milestone == 60L) "" else "s"}"
                    else -> "$milestone second${if (milestone == 1L) "" else "s"}"
                }
                game.arenaWorld.players.forEach {
                    it.sendMessage("${CC.B_YELLOW}$text ${CC.YELLOW}left in Pillar of Fortune!")
                    if (milestone <= 10L) XSound.BLOCK_NOTE_BLOCK_PLING.play(it)
                }
            }
        }, 20L, 20L).bindWith(this)
    }

    private fun startDeathmatch()
    {
        if (!deathmatchActive.compareAndSet(false, true)) return

        game.arenaWorld.players.forEach {
            it.sendMessage("${CC.B_RED}Sudden Death! ${CC.RED}Time's up — players now lose ${CC.WHITE}½❤ ${CC.RED}every second!")
            XSound.ENTITY_WITHER_SPAWN.play(it)
        }

        aliveResources().forEach { resources ->
            val player = resources.toPlayer() ?: return@forEach
            player.foodLevel = 6
            player.saturation = 0f
        }

        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }
            aliveResources().forEach { resources ->
                val player = resources.toPlayer() ?: return@forEach
                val newHealth = player.health - configuration.deathmatchDamagePerTick
                if (newHealth <= 0.0)
                {
                    markEliminated(resources, lastDamager[resources.player], cause = "succumbed to sudden death.")
                } else
                {
                    player.health = newHealth
                }
            }
        }, configuration.deathmatchDamageIntervalTicks, configuration.deathmatchDamageIntervalTicks).bindWith(this)
    }
}
