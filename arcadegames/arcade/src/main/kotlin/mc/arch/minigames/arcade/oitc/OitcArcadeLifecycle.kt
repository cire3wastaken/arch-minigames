package mc.arch.minigames.arcade.oitc

import gg.tropic.practice.expectation.ExpectationService.returnToSpawnItem
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.games.event.PlayerSelectSpawnLocationEvent
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameEvent
import gg.tropic.practice.minigame.MiniGameLifecycle
import gg.tropic.practice.minigame.MiniGameScoreboard
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import gg.tropic.practice.minigame.event.functionality.MiniGamePlayerDeathEvent
import gg.tropic.practice.strategies.MarkSpectatorStrategy
import mc.arch.minigames.arcade.broadcast.ArcadeBroadcastTrigger
import mc.arch.minigames.arcade.ArcadeMiniGameConfiguration
import mc.arch.minigames.arcade.ArcadeTypeMetadata
import mc.arch.minigames.arcade.privategames.ArcadePrivateGameSettings
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.nametag.NametagHandler
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.visibility.VisibilityHandler
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Arrow
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.lang.AutoCloseable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class OitcArcadeLifecycle(
    override val configuration: ArcadeMiniGameConfiguration,
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    override val typeConfiguration: MiniGameTypeMetadata = ArcadeTypeMetadata,
    override val events: List<MiniGameEvent> = listOf()
) : MiniGameLifecycle<ArcadeMiniGameConfiguration>
{
    private val playerResources = ConcurrentHashMap<UUID, OitcPlayerResources>()
    private val gameEnded = AtomicBoolean(false)
    private var gameStartTime = 0L

    // Kills required to win, overridable via private-game settings.
    private val killTarget: Int = game.expectationModel.privateGameSettings
        ?.getSetting(ArcadePrivateGameSettings.OITC_KILL_TARGET, OitcConstants.KILL_TARGET)
        ?: OitcConstants.KILL_TARGET

    // Spawn protection (ms) and respawn delay (ticks), overridable via private-game settings.
    private val spawnProtectionMs: Long = run {
        val seconds = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.OITC_SPAWN_PROTECTION, ArcadePrivateGameSettings.DEFAULT_OITC_SPAWN_PROTECTION)
            ?: ArcadePrivateGameSettings.DEFAULT_OITC_SPAWN_PROTECTION
        seconds * 1000L
    }
    private val respawnDelayTicks: Long = run {
        val seconds = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.OITC_RESPAWN_DELAY, ArcadePrivateGameSettings.DEFAULT_OITC_RESPAWN_DELAY)
            ?: ArcadePrivateGameSettings.DEFAULT_OITC_RESPAWN_DELAY
        seconds * 20L
    }

    override val scoreboard: MiniGameScoreboard = OitcScoreboard(game, configuration, killTarget) { playerResources.values }
    private val backingTerminable = CompositeTerminable.create()

    private fun toPlayerResources(player: Player) = playerResources
        .getOrPut(player.uniqueId) {
            OitcPlayerResources(player = player.uniqueId, username = player.name)
        }

    private fun pickRandomSpawn(): Location?
    {
        val spawns = game.map.findSpawnLocations()
        if (spawns.isNotEmpty())
        {
            return spawns.random().position.toLocation(game.arenaWorld)
        }

        return game.map.findSpawnLocationMatchingTeam(TeamIdentifier.A)
            ?.toLocation(game.arenaWorld)
    }

    private fun respawnPlayer(resources: OitcPlayerResources)
    {
        if (gameEnded.get()) return

        val player = resources.toPlayer() ?: return
        if (resources.spectator) return

        GameService.spectatorPlayers -= player.uniqueId
        GameService.lightSpectatorPlayers -= player.uniqueId

        player.allowFlight = false
        player.isFlying = false

        pickRandomSpawn()?.let(player::teleport)
        OitcLoadout.apply(player)
        resources.lastSpawnTime = System.currentTimeMillis()
        resources.pendingRespawn = false

        game.arenaWorld.players.forEach { other ->
            VisibilityHandler.update(other)
            NametagHandler.reloadPlayer(player, other)
        }
    }

    private fun isInSpawnProtection(resources: OitcPlayerResources): Boolean
    {
        if (resources.lastSpawnTime == 0L) return false
        return System.currentTimeMillis() - resources.lastSpawnTime < spawnProtectionMs
    }

    private fun handleKill(killer: OitcPlayerResources, victim: OitcPlayerResources, byBow: Boolean, bowDistance: Double? = null)
    {
        if (gameEnded.get()) return

        killer.kills += 1
        victim.deaths += 1

        OitcRewards.awardKill(killer.player, byBow, bowDistance)

        val killerPlayer = killer.toPlayer()
        val victimPlayer = victim.toPlayer()

        if (killerPlayer != null)
        {
            val inventory = killerPlayer.inventory
            val arrowSlot = inventory.getItem(8)
            if (arrowSlot == null || arrowSlot.type == Material.AIR)
            {
                inventory.setItem(8, ItemStack(Material.ARROW, 1))
            } else if (arrowSlot.type == Material.ARROW)
            {
                arrowSlot.amount = arrowSlot.amount + 1
                inventory.setItem(8, arrowSlot)
            } else
            {
                inventory.addItem(ItemStack(Material.ARROW, 1))
            }
            killerPlayer.updateInventory()
        }

        game.arenaWorld.players.forEach { it.playSound(it.location, Sound.NOTE_PLING, 1.0f, 2.0f) }

        if (killerPlayer != null && victimPlayer != null)
        {
            val verb = if (byBow) "shot" else "slashed"
            val distanceSuffix = if (byBow && bowDistance != null)
            {
                " ${CC.GRAY}from ${CC.WHITE}${"%.1f".format(bowDistance)}${CC.GRAY} blocks away"
            } else ""

            game.arenaWorld.players.forEach {
                it.sendMessage(
                    "${CC.RED}${victimPlayer.name} ${CC.GRAY}was $verb by ${CC.GREEN}${killerPlayer.name}$distanceSuffix " +
                        "${CC.D_GRAY}(${CC.WHITE}${killer.kills}${CC.D_GRAY}/${CC.WHITE}${killTarget}${CC.D_GRAY})"
                )
            }
        }

        if (killer.kills >= killTarget)
        {
            handleGameWin(killer)
        }
    }

    private fun handleGameWin(winner: OitcPlayerResources)
    {
        if (gameEnded.getAndSet(true)) return

        OitcRewards.awardWinner(winner)
        OitcRewards.awardParticipants(playerResources.values, winner.player)

        val winnerName = winner.toPlayer()?.name ?: winner.username
        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.B_GOLD}$winnerName ${CC.GOLD}won OITC with ${CC.WHITE}${winner.kills}${CC.GOLD} kills!")
            player.playSound(player.location, Sound.FIREWORK_LAUNCH, 1.0f, 1.0f)
        }

        var times = 0L
        Schedulers.async()
            .runRepeating({ task ->
                if (times >= 3)
                {
                    task.closeAndReportException()
                    return@runRepeating
                }
                times += 1
                game.arenaWorld.players.forEach { it.playSound(it.location, Sound.FIREWORK_LAUNCH, 1.0f, 1.0f) }
            }, 0L, 20L)
            .bindWith(this)

        Schedulers.async()
            .runLater({ game.complete(null) }, 100L)
            .bindWith(this)
    }

    private fun arrowShooterIfPlayer(damager: Entity): Player?
    {
        if (damager !is Arrow) return null
        val shooter = damager.shooter as? Player ?: return null
        val resources = playerResources[shooter.uniqueId] ?: return null
        if (resources.spectator || resources.pendingRespawn) return null
        return shooter
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
                    GameService.byPlayer(it.entity as Player) == game
            }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(FoodLevelChangeEvent::class.java)
            .filter { it.entity is Player && GameService.byPlayer(it.entity as Player) == game }
            .handler { event ->
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

        // Despawn arrows the moment they land so missed shots can't be picked up
        Events
            .subscribe(ProjectileHitEvent::class.java)
            .filter { it.entity is Arrow }
            .filter {
                val shooter = (it.entity as Arrow).shooter as? Player ?: return@filter false
                GameService.byPlayer(shooter) == game
            }
            .handler { event ->
                Schedulers.sync().runLater({ event.entity.remove() }, 1L).bindWith(this)
            }
            .bindWith(this)

        Events
            .subscribe(MiniGamePlayerDeathEvent::class.java)
            .filter { it.game === this }
            .handler(::handlePlayerDeath)
            .bindWith(this)

        Events
            .subscribe(PlayerDropItemEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game == game }
            .handler(::handlePlayerJoin)
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
                playerResources.remove(event.player.uniqueId)

                if (gameEnded.get() || !game.state(GameState.Playing)) return@handler

                val remainingFighters = playerResources.values.filter { !it.spectator }
                when (remainingFighters.size)
                {
                    1 -> handleGameWin(remainingFighters.first())
                    0 ->
                    {
                        gameEnded.set(true)
                        game.complete(null)
                    }
                }
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
        if (gameEnded.get())
        {
            event.isCancelled = true
            return
        }

        val victim = event.entity as Player
        val victimResources = playerResources[victim.uniqueId] ?: return

        // Victim is already dead/respawning or not a fighter — never apply damage and never count kills.
        if (victimResources.spectator || victimResources.pendingRespawn)
        {
            event.isCancelled = true
            return
        }

        val shooter = arrowShooterIfPlayer(event.damager)
        if (shooter != null)
        {
            event.isCancelled = true

            if (shooter.uniqueId == victim.uniqueId) return

            val shooterResources = playerResources[shooter.uniqueId] ?: return
            if (shooterResources.spectator || shooterResources.pendingRespawn) return

            if (isInSpawnProtection(victimResources))
            {
                shooter.sendMessage("${CC.RED}That player just spawned in!")
                return
            }

            val distance = runCatching {
                if (shooter.world == victim.world) shooter.location.distance(victim.location) else null
            }.getOrNull()

            triggerKill(shooterResources, victimResources, byBow = true, bowDistance = distance)
            return
        }

        val attacker = event.damager as? Player ?: return
        val attackerResources = playerResources[attacker.uniqueId]
        if (attackerResources == null || attackerResources.spectator || attackerResources.pendingRespawn)
        {
            event.isCancelled = true
            return
        }
        if (isInSpawnProtection(victimResources))
        {
            event.isCancelled = true
            attacker.sendMessage("${CC.RED}That player just spawned in!")
            return
        }

        // Sword fights: let damage flow naturally, but if it would be lethal, intercept and respawn instead.
        val finalDamage = event.finalDamage
        if (victim.health - finalDamage <= 0.0)
        {
            event.isCancelled = true
            triggerKill(attackerResources, victimResources, byBow = false)
        }
    }

    private fun triggerKill(killer: OitcPlayerResources, victim: OitcPlayerResources, byBow: Boolean, bowDistance: Double? = null)
    {
        if (gameEnded.get()) return

        // Latch immediately so any other in-flight damage events for this victim are rejected
        if (victim.pendingRespawn) return
        victim.pendingRespawn = true

        handleKill(killer, victim, byBow, bowDistance)

        // We cancelled the damage event so the victim is still alive. Make them a true spectator
        val victimPlayer = victim.toPlayer()
        if (victimPlayer != null)
        {
            victimPlayer.inventory.clear()
            victimPlayer.inventory.armorContents = arrayOfNulls(4)
            victimPlayer.health = victimPlayer.maxHealth
            victimPlayer.foodLevel = 20
            victimPlayer.fireTicks = 0
            victimPlayer.activePotionEffects.forEach { victimPlayer.removePotionEffect(it.type) }
            victimPlayer.allowFlight = true
            victimPlayer.isFlying = true
            victimPlayer.teleport(game.spectatorLocation())
            victimPlayer.playSound(victimPlayer.location, Sound.HURT_FLESH, 1.0f, 1.0f)

            GameService.spectatorPlayers += victimPlayer.uniqueId
            NametagHandler.reloadPlayer(victimPlayer)
            VisibilityHandler.update(victimPlayer)

            game.arenaWorld.players.forEach { other ->
                VisibilityHandler.update(other)
                NametagHandler.reloadPlayer(victimPlayer, other)
            }
        }

        if (gameEnded.get()) return

        Schedulers.sync().runLater({
            if (gameEnded.get()) return@runLater
            if (victim.spectator) return@runLater
            respawnPlayer(victim)
        }, respawnDelayTicks).bindWith(this)
    }

    private fun handlePlayerDeath(event: MiniGamePlayerDeathEvent)
    {
        val victim = event.player
        val victimResources = playerResources[victim.uniqueId] ?: return
        event.drops.clear()

        if (victimResources.pendingRespawn) return
        victimResources.pendingRespawn = true

        victimResources.deaths += 1
        game.arenaWorld.players.forEach {
            it.sendMessage("${CC.RED}${victim.name} ${CC.GRAY}died.")
        }

        if (gameEnded.get()) return

        Schedulers.sync().runLater({
            if (gameEnded.get()) return@runLater
            val resources = playerResources[victim.uniqueId] ?: return@runLater
            if (resources.spectator) return@runLater
            respawnPlayer(resources)
        }, respawnDelayTicks).bindWith(this)
    }

    private fun handlePlayerJoin(event: PlayerJoinGameEvent)
    {
        if (game.state == GameState.Waiting || game.state == GameState.Starting)
        {
            event.player.inventory.setItem(8, returnToSpawnItem)
            event.player.updateInventory()
            ArcadeBroadcastTrigger.giveItemIfPermitted(event.player, slot = 0)

            toPlayerResources(event.player)
            return
        }

        MarkSpectatorStrategy.markSpectator(
            player = event.player,
            world = game.arenaWorld,
            shouldAnnounce = false
        )

        playerResources[event.player.uniqueId] = OitcPlayerResources(
            player = event.player.uniqueId,
            username = event.player.name,
            spectator = true
        )
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handleGameStart(event: GameStartEvent)
    {
        gameStartTime = System.currentTimeMillis()

        game.arenaWorld.players.forEach { player ->
            val resources = toPlayerResources(player)
            if (resources.spectator) return@forEach
            respawnPlayer(resources)
        }

        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.B_GREEN}OITC has started! ${CC.GRAY}First to ${CC.WHITE}${killTarget}${CC.GRAY} kills wins!")
            player.playSound(player.location, Sound.LEVEL_UP, 1.0f, 1.0f)
        }

        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.closeAndReportException()
                return@runRepeating
            }

            if (System.currentTimeMillis() - gameStartTime > OitcConstants.GAME_TIMEOUT_MS)
            {
                val leader = playerResources.values
                    .filter { !it.spectator }
                    .maxByOrNull { it.kills }
                if (leader != null)
                {
                    game.arenaWorld.players.forEach {
                        it.sendMessage("${CC.B_YELLOW}OITC time limit reached! Highest kill count wins.")
                    }
                    handleGameWin(leader)
                } else
                {
                    gameEnded.set(true)
                    game.complete(null)
                }
                task.closeAndReportException()
            }
        }, 20L, 20L).bindWith(this)
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
