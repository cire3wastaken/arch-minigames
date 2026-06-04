package mc.arch.minigames.arcade.rlgl

import com.cryptomorin.xseries.XSound
import gg.tropic.practice.expectation.ExpectationService.returnToSpawnItem
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
import com.cryptomorin.xseries.XMaterial
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
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
import java.lang.AutoCloseable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class RlglArcadeLifecycle(
    override val configuration: ArcadeMiniGameConfiguration,
    override val game: AbstractMiniGameGameImpl<ArcadeMiniGameConfiguration>,
    override val typeConfiguration: MiniGameTypeMetadata = ArcadeTypeMetadata,
    override val events: List<MiniGameEvent> = listOf()
) : MiniGameLifecycle<ArcadeMiniGameConfiguration>
{
    private enum class LightState { GREEN, RED }

    private val playerResources = ConcurrentHashMap<UUID, RlglPlayerResources>()
    private val lastDamager = ConcurrentHashMap<UUID, UUID>()
    private val gameEnded = AtomicBoolean(false)
    private val timingOut = AtomicBoolean(false)
    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val stunnedPlayers = ConcurrentHashMap.newKeySet<UUID>()
    private val assignedSpawns = ConcurrentHashMap<UUID, Location>()
    private val finishers = ConcurrentLinkedQueue<UUID>()

    private val lightState = AtomicReference(LightState.GREEN)
    private var lightChangedAt = 0L
    private var nextLightChangeAt = 0L
    private var redLethalAt = 0L

    private var finishMinX = 0.0
    private var finishMaxX = 0.0
    private var finishMinZ = 0.0
    private var finishMaxZ = 0.0
    private var finishY = 0
    private var hasFinishRegion = false

    private var gameStartTime = 0L

    // Finishers required to end the game, overridable via private-game settings.
    private val finishTarget: Int = game.expectationModel.privateGameSettings
        ?.getSetting(ArcadePrivateGameSettings.RLGL_FINISH_TARGET, RlglConstants.FINISH_TARGET)
        ?: RlglConstants.FINISH_TARGET

    // Spawn protection (ms) and game timeout (ms), overridable via private-game settings.
    private val spawnProtectionMs: Long = run {
        val seconds = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.RLGL_SPAWN_PROTECTION, ArcadePrivateGameSettings.DEFAULT_RLGL_SPAWN_PROTECTION)
            ?: ArcadePrivateGameSettings.DEFAULT_RLGL_SPAWN_PROTECTION
        seconds * 1000L
    }
    private val gameTimeoutMs: Long = run {
        val minutes = game.expectationModel.privateGameSettings
            ?.getSetting(ArcadePrivateGameSettings.RLGL_GAME_TIMEOUT, ArcadePrivateGameSettings.DEFAULT_RLGL_GAME_TIMEOUT)
            ?: ArcadePrivateGameSettings.DEFAULT_RLGL_GAME_TIMEOUT
        minutes * 60_000L
    }

    private fun remainingMs(): Long
    {
        if (gameStartTime == 0L) return gameTimeoutMs
        return (gameTimeoutMs - (System.currentTimeMillis() - gameStartTime)).coerceAtLeast(0L)
    }

    private fun msUntilLightChange(): Long =
        (nextLightChangeAt - System.currentTimeMillis()).coerceAtLeast(0L)

    override val scoreboard: MiniGameScoreboard = RlglScoreboard(
        game,
        configuration,
        resourcesProvider = { playerResources.values },
        remainingMs = ::remainingMs,
        isRedLight = { lightState.get() == LightState.RED },
        finishCount = { finishers.size },
        finishTarget = finishTarget
    )

    private val backingTerminable = CompositeTerminable.create()

    private fun toPlayerResources(player: Player) = playerResources
        .getOrPut(player.uniqueId) {
            RlglPlayerResources(player = player.uniqueId, username = player.name)
        }

    private fun fallbackSpawn(): Location? =
        game.map.findSpawnLocationMatchingTeam(TeamIdentifier.A)
            ?.toLocation(game.arenaWorld)

    private fun findSpawn(id: String): Location? = game.map.findSpawnLocations()
        .firstOrNull { it.id == id }
        ?.position
        ?.toLocation(game.arenaWorld)

    private fun isInFinishRegion(loc: Location): Boolean
    {
        if (!hasFinishRegion) return false
        return loc.x in finishMinX..finishMaxX
            && loc.z in finishMinZ..finishMaxZ
            && kotlin.math.abs(loc.blockY - finishY) <= 3
    }

    private fun finishCenter(): Location? = if (hasFinishRegion)
        Location(
            game.arenaWorld,
            (finishMinX + finishMaxX) / 2.0,
            finishY.toDouble() + 1.0,
            (finishMinZ + finishMaxZ) / 2.0
        )
    else null

    private fun assignSpawnsTo(participants: List<RlglPlayerResources>)
    {
        assignedSpawns.clear()
        if (participants.isEmpty()) return

        val a = findSpawn("start-a")
        val b = findSpawn("start-b")
        if (a == null || b == null)
        {
            val fallbacks = game.map.findSpawnLocations()
                .filter { it.id != "spec" && !it.id.startsWith("finish") && !it.id.startsWith("start") }
                .map { it.position.toLocation(game.arenaWorld) }
                .shuffled()
                .toMutableList()
            val fb = fallbackSpawn()
            participants.forEachIndexed { index, resources ->
                val loc = fallbacks.getOrNull(index) ?: fb ?: return@forEachIndexed
                assignedSpawns[resources.player] = loc
            }
            return
        }

        val target = finishCenter()
        val count = participants.size

        participants.shuffled().forEachIndexed { index, resources ->
            val t = if (count == 1) 0.5 else index.toDouble() / (count - 1).toDouble()
            val x = a.x + (b.x - a.x) * t
            val z = a.z + (b.z - a.z) * t
            val yaw = if (target != null) yawToward(x, z, target.x, target.z) else a.yaw
            assignedSpawns[resources.player] = Location(a.world, x, a.y, z, yaw, 0f)
        }
    }

    private fun yawToward(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Float
    {
        val dx = toX - fromX
        val dz = toZ - fromZ

        return Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
    }

    private fun computeFinishRegion()
    {
        val a = findSpawn("finish-a")
        val b = findSpawn("finish-b")
        if (a == null || b == null)
        {
            hasFinishRegion = false
            return
        }
        finishMinX = minOf(a.x, b.x)
        finishMaxX = maxOf(a.x, b.x)
        finishMinZ = minOf(a.z, b.z)
        finishMaxZ = maxOf(a.z, b.z)
        finishY = minOf(a.blockY, b.blockY)
        hasFinishRegion = true
    }

    private fun spawnPlayer(resources: RlglPlayerResources)
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
        RlglLoadout.apply(player)
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

    private fun isInSpawnProtection(resources: RlglPlayerResources): Boolean
    {
        if (resources.lastSpawnTime == 0L) return false
        return System.currentTimeMillis() - resources.lastSpawnTime < spawnProtectionMs
    }

    private fun aliveResources(): List<RlglPlayerResources> = playerResources.values
        .filter { !it.spectator && it.crossedFinishAt == 0L && it.toPlayer() != null }

    private fun activeParticipant(player: Player): RlglPlayerResources?
    {
        if (GameService.byPlayer(player) != game) return null
        val resources = playerResources[player.uniqueId] ?: return null
        if (resources.spectator) return null
        if (resources.crossedFinishAt != 0L) return null
        return resources
    }

    private fun isPreGameFrozen(uuid: UUID): Boolean = uuid in frozenPlayers

    private fun isRedLightLethal(): Boolean =
        lightState.get() == LightState.RED && System.currentTimeMillis() >= redLethalAt

    private fun markEliminated(victim: RlglPlayerResources, killerId: UUID?, cause: String)
    {
        if (victim.spectator) return
        victim.spectator = true

        val killerResources = killerId
            ?.let(playerResources::get)
            ?.takeUnless { it.spectator || it.player == victim.player }
        if (killerResources != null)
        {
            killerResources.kills += 1
            RlglRewards.awardKill(killerResources.player)
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
            "${CC.RED}$victimName ${CC.GRAY}was eliminated by ${CC.GREEN}${killerPlayer.name}"
        else
            "${CC.RED}$victimName ${CC.GRAY}$cause"
        game.arenaWorld.players.forEach {
            it.sendMessage(message)
            XSound.BLOCK_NOTE_BLOCK_PLING.play(it)
        }

        lastDamager.remove(victim.player)
        checkWinCondition()
    }

    private fun markFinisher(resources: RlglPlayerResources)
    {
        if (resources.crossedFinishAt != 0L || resources.spectator) return
        resources.crossedFinishAt = System.currentTimeMillis()
        finishers += resources.player
        resources.finishPlace = finishers.size
        RlglRewards.awardFinish(resources.player)

        val player = resources.toPlayer()
        val place = resources.finishPlace
        val placeText = when (place)
        {
            1 -> "${CC.B_GOLD}1st"
            2 -> "${CC.B_YELLOW}2nd"
            3 -> "${CC.B_AQUA}3rd"
            else -> "${CC.WHITE}#$place"
        }
        game.arenaWorld.players.forEach {
            it.sendMessage("$placeText ${CC.GOLD}${resources.username} ${CC.GRAY}crossed the finish!")
            XSound.ENTITY_PLAYER_LEVELUP.play(it)
        }

        if (player != null)
        {
            player.inventory.clear()
            player.inventory.armorContents = arrayOfNulls(4)
            player.activePotionEffects.forEach { player.removePotionEffect(it.type) }
            player.health = player.maxHealth
            player.foodLevel = 20
            player.fireTicks = 0
            player.fallDistance = 0f
            finishCenter()?.let(player::teleport)
            player.gameMode = GameMode.ADVENTURE
        }
        checkWinCondition()
    }

    private fun checkWinCondition()
    {
        if (gameEnded.get()) return
        if (timingOut.get()) return
        if (finishers.size >= finishTarget)
        {
            handleGameEnd(timeoutReached = false)
            return
        }
        if (aliveResources().isEmpty())
        {
            handleGameEnd(timeoutReached = false)
        }
    }

    private fun handleGameEnd(timeoutReached: Boolean)
    {
        if (gameEnded.getAndSet(true)) return

        val finisherResources = finishers
            .mapNotNull { playerResources[it] }
        RlglRewards.awardFinishers(finisherResources)
        RlglRewards.awardParticipants(
            playerResources.values,
            alreadyRewarded = finisherResources.map { it.player }.toSet()
        )

        val winnerName = finisherResources.firstOrNull()?.let { it.toPlayer()?.name ?: it.username }
        game.arenaWorld.players.forEach { player ->
            if (winnerName != null)
            {
                val prefix = if (timeoutReached) "${CC.B_YELLOW}Time's up! " else ""
                player.sendMessage("${prefix}${CC.B_GOLD}$winnerName ${CC.GOLD}won Red Light, Green Light!")
            } else
            {
                player.sendMessage("${CC.GOLD}Red Light, Green Light ended with no finishers.")
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
        game.shouldAllowFriendlyFire = true
        game.shouldAllowCrafting = false
        game.isMinIndependent = true
        // No placing/breaking blocks in RLGL — players race across a fixed track.
        game.kit.features.remove(FeatureFlag.PlaceBlocks)
        game.kit.features.remove(FeatureFlag.BreakPlacedBlocks)
        game.kit.features.remove(FeatureFlag.BuildLimit)
        game.kit.features.remove(FeatureFlag.DoNotTakeDamage)

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
            .filter { GameService.byPlayer(it.player) == game }
            .handler { it.isCancelled = true }
            .bindWith(this)
        Events
            .subscribe(BlockBreakEvent::class.java, EventPriority.HIGHEST)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerDropItemEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .filter { game.state(GameState.Waiting) || game.state(GameState.Starting) || it.player.uniqueId in frozenPlayers }
            .handler { it.isCancelled = true }
            .bindWith(this)

        Events
            .subscribe(PlayerMoveEvent::class.java)
            .handler { event ->
                val uuid = event.player.uniqueId
                if (isPreGameFrozen(uuid))
                {
                    val from = event.from
                    val to = event.to ?: return@handler
                    if (from.blockX != to.blockX || from.blockZ != to.blockZ || from.y != to.y)
                    {
                        event.setTo(Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch))
                    }
                    return@handler
                }

                val resources = playerResources[uuid] ?: return@handler

                // Finished players are locked inside the finish rectangle so they can't
                // walk back into the racing field.
                if (resources.crossedFinishAt != 0L)
                {
                    val to = event.to ?: return@handler
                    if (!isInFinishRegion(to))
                    {
                        val from = event.from
                        event.setTo(Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch))
                    }
                    return@handler
                }

                if (resources.spectator) return@handler

                // Freeze-wand stun: lock position. Stunned players can rotate but not move,
                // so red-light detection won't fire while stunned (block-coord delta == 0).
                if (uuid in stunnedPlayers)
                {
                    val from = event.from
                    val to = event.to ?: return@handler
                    if (from.blockX != to.blockX || from.blockZ != to.blockZ || from.y != to.y)
                    {
                        event.setTo(Location(from.world, from.x, from.y, from.z, to.yaw, to.pitch))
                    }
                    return@handler
                }

                if (!isRedLightLethal()) return@handler

                val from = event.from
                val to = event.to ?: return@handler
                if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) return@handler

                RlglRewards.awardCaught(resources.player)
                markEliminated(resources, lastDamager[uuid], cause = "moved on red light.")
            }
            .bindWith(this)

        Events
            .subscribe(PlayerInteractEvent::class.java, EventPriority.LOWEST)
            .filter { it.player.uniqueId in frozenPlayers }
            .handler { it.isCancelled = true }
            .bindWith(this)

        // Sprint Boots pickup — consume and apply 30s speed.
        Events
            .subscribe(PlayerPickupItemEvent::class.java)
            .filter { GameService.byPlayer(it.player) == game }
            .handler { event ->
                val resources = playerResources[event.player.uniqueId] ?: return@handler
                if (resources.spectator || resources.crossedFinishAt != 0L) return@handler
                val stack = event.item.itemStack
                if (XMaterial.matchXMaterial(stack) != XMaterial.LEATHER_BOOTS) return@handler
                val name = stack.itemMeta?.displayName ?: return@handler
                if (!name.contains("Sprint Boots")) return@handler
                event.isCancelled = true
                event.item.remove()
                event.player.addPotionEffect(RlglLootTable.sprintEffect(), true)
                event.player.sendMessage("${CC.AQUA}Sprint Boots${CC.GRAY} — speed for 30s!")
                XSound.ENTITY_PLAYER_LEVELUP.play(event.player)
            }
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
                    RlglPlayerResources(
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
                if (resources != null && !resources.spectator && resources.crossedFinishAt == 0L && !gameEnded.get())
                {
                    markEliminated(resources, lastDamager[resources.player], cause = "disconnected.")
                } else if (resources == null || resources.crossedFinishAt == 0L)
                {
                    // Finishers stay in the map so their reward attribution survives.
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
        if (gameEnded.get())
        {
            event.isCancelled = true
            return
        }

        // No combat outside of the actual race — covers the waiting room, the lobby
        // countdown, the pre-game freeze, and the end-of-game phase.
        if (!game.state(GameState.Playing) || frozenPlayers.isNotEmpty())
        {
            event.isCancelled = true
            return
        }

        val victim = event.entity as Player
        val victimResources = activeParticipant(victim) ?: run {
            event.isCancelled = true
            return
        }
        if (victimResources.crossedFinishAt != 0L)
        {
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

        // Freeze Wand: consume the item, cancel damage, and stun the victim for 2-5s.
        val attackerHand = attacker.itemInHand
        if (attackerHand != null && attackerHand.itemMeta?.displayName == RlglLootTable.FREEZE_WAND_DISPLAY_NAME)
        {
            event.isCancelled = true
            if (attackerHand.amount > 1) attackerHand.amount -= 1
            else attacker.inventory.setItemInHand(null)
            attacker.updateInventory()
            stunPlayer(victim)
            attacker.sendMessage("${CC.AQUA}You froze ${CC.WHITE}${victim.name}${CC.AQUA}!")
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
        if (victimResources.spectator || victimResources.crossedFinishAt != 0L)
        {
            event.isCancelled = true
            return
        }

        if (victim.health - event.finalDamage <= 0.0)
        {
            event.isCancelled = true
            val cause = when (event.cause)
            {
                EntityDamageEvent.DamageCause.FALL -> "fell to their death."
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
        val killerId = (event.killer as? Player)?.uniqueId ?: lastDamager[victim.uniqueId]
        markEliminated(victimResources, killerId, cause = "died.")
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

        event.player.gameMode = GameMode.SPECTATOR
        refreshSpectatorVisibility(event.player)

        playerResources[event.player.uniqueId] = RlglPlayerResources(
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
        // Order matters: finish region is used to compute the start-spawn yaw so racers
        // always face the finish line regardless of how the start-a/b signs were placed.
        computeFinishRegion()
        assignSpawnsTo(participants)

        game.arenaWorld.setSpawnFlags(true, true)
        game.voidDamageMin = null

        game.arenaWorld.players.forEach { player ->
            val resources = toPlayerResources(player)
            if (resources.spectator) return@forEach
            spawnPlayer(resources)
            frozenPlayers += player.uniqueId
        }

        game.arenaWorld.players.forEach { player ->
            player.sendMessage(" ")
            player.sendMessage("${CC.B_RED}>>> TURN YOUR SOUND UP! <<<")
            player.sendMessage("${CC.B_RED}This game runs on audio cues — note blocks and goat horns signal the lights.")
            player.sendMessage(" ")
        }

        runFreezeCountdownThenBegin()
    }

    private fun runFreezeCountdownThenBegin()
    {
        val secondsRemaining = AtomicInteger(RlglConstants.FREEZE_COUNTDOWN_SECONDS)
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
            val message = "${CC.RED}Race begins in ${CC.WHITE}${remaining}s${CC.RED}..."
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
            RlglLoadout.apply(player)
            player.gameMode = GameMode.SURVIVAL
            GameService.spectatorPlayers -= player.uniqueId
            GameService.lightSpectatorPlayers -= player.uniqueId
            resources.lastSpawnTime = System.currentTimeMillis()
            XSound.ENTITY_PLAYER_LEVELUP.play(player)
        }
        frozenPlayers.clear()

        // Set initial light state — start on GREEN, schedule first flip.
        lightState.set(LightState.GREEN)
        lightChangedAt = System.currentTimeMillis()
        nextLightChangeAt = lightChangedAt + scaledRandom(RlglConstants.GREEN_MIN_MS, RlglConstants.GREEN_MAX_MS)
        redLethalAt = 0L

        game.arenaWorld.players.forEach { player ->
            player.sendMessage("${CC.B_RED}The Race has begun! ${CC.GRAY}Reach the finish — first ${CC.WHITE}${finishTarget}${CC.GRAY} win.")
            player.sendMessage("${CC.GRAY}On ${CC.B_RED}RED LIGHT ${CC.GRAY}any movement = elimination. Grab loot to sabotage others!")
        }

        startLightController()
        startFinishLinePoller()
        startTimeoutWatcher()
        startLootSpawner()
    }

    private fun randomBetween(minMs: Long, maxMs: Long): Long =
        ThreadLocalRandom.current().nextLong(minMs, maxMs + 1)

    private fun stunPlayer(victim: Player)
    {
        val durationMs = randomBetween(2_000L, 5_000L)
        stunnedPlayers += victim.uniqueId
        XSound.BLOCK_GLASS_BREAK.play(victim)
        Schedulers.sync().runLater({
            stunnedPlayers -= victim.uniqueId
            val p = Bukkit.getPlayer(victim.uniqueId)
            if (p != null && p.isOnline) XSound.BLOCK_NOTE_BLOCK_PLING.play(p)
        }, (durationMs / 50)).bindWith(this)
    }

    // Stretch light phases by 1.5× when 12+ racers remain — keeps the field from being
    // a coin-flip when there's still a crowd.
    private fun scaledRandom(minMs: Long, maxMs: Long): Long
    {
        val factor = if (aliveResources().size >= 12) 1.5 else 1.0
        return randomBetween((minMs * factor).toLong(), (maxMs * factor).toLong())
    }

    private fun startLightController()
    {
        var warnedForNextRed = false
        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }

            val now = System.currentTimeMillis()

            // Pre-flip warning when next state is RED.
            if (lightState.get() == LightState.GREEN
                && !warnedForNextRed
                && nextLightChangeAt - now <= 800L)
            {
                warnedForNextRed = true

                listOf(0L, 5L, 10L).forEach { delay ->
                    Schedulers.sync().runLater({
                        if (gameEnded.get()) return@runLater
                        game.arenaWorld.players.forEach { p ->
                            XSound.BLOCK_NOTE_BLOCK_PLING.play(p)
                        }
                    }, delay).bindWith(this)
                }
            }

            if (now < nextLightChangeAt) return@runRepeating

            // Flip
            val prior = lightState.get()
            val next = if (prior == LightState.GREEN) LightState.RED else LightState.GREEN
            lightState.set(next)
            lightChangedAt = now
            warnedForNextRed = false

            when (next)
            {
                LightState.RED ->
                {
                    redLethalAt = now + 600L
                    lastDamager.clear()
                    nextLightChangeAt = now + scaledRandom(RlglConstants.RED_MIN_MS, RlglConstants.RED_MAX_MS)

                    game.arenaWorld.players.forEach { p ->
                        XSound.ITEM_GOAT_HORN_SOUND_6.play(p)
                    }
                }
                LightState.GREEN ->
                {
                    redLethalAt = 0L
                    lastDamager.clear()
                    nextLightChangeAt = now + scaledRandom(RlglConstants.GREEN_MIN_MS, RlglConstants.GREEN_MAX_MS)

                    game.arenaWorld.players.forEach { p ->
                        XSound.ITEM_GOAT_HORN_SOUND_1.play(p)
                    }
                }
            }
        }, 2L, 2L).bindWith(this)
    }

    private fun startFinishLinePoller()
    {
        if (!hasFinishRegion) return
        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }
            aliveResources().forEach { resources ->
                if (resources.crossedFinishAt != 0L) return@forEach
                val player = resources.toPlayer() ?: return@forEach
                val loc = player.location
                if (loc.x in finishMinX..finishMaxX
                    && loc.z in finishMinZ..finishMaxZ
                    && kotlin.math.abs(loc.blockY - finishY) <= 3)
                {
                    markFinisher(resources)
                }
            }
        }, 2L, 2L).bindWith(this)
    }

    private fun startTimeoutWatcher()
    {
        val announcedMilestones = ConcurrentHashMap.newKeySet<Long>()
        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }
            val elapsed = System.currentTimeMillis() - gameStartTime
            val remaining = gameTimeoutMs - elapsed
            if (remaining <= 0L)
            {
                timingOut.set(true)
                aliveResources().toList().forEach { resources ->
                    markEliminated(resources, killerId = null, cause = "ran out of time.")
                }
                handleGameEnd(timeoutReached = true)
                task.close()
                return@runRepeating
            }

            val remainingSec = remaining / 1000
            val milestone = when
            {
                remainingSec in 1..10 -> remainingSec
                remainingSec == 30L -> 30L
                remainingSec == 60L -> 60L
                remainingSec == 120L -> 120L
                else -> null
            }
            if (milestone != null && announcedMilestones.add(milestone))
            {
                val text = if (milestone >= 60L) "${milestone / 60} minute${if (milestone == 60L) "" else "s"}"
                else "$milestone second${if (milestone == 1L) "" else "s"}"
                game.arenaWorld.players.forEach {
                    it.sendMessage("${CC.B_YELLOW}$text ${CC.YELLOW}left to finish!")
                    if (milestone <= 10L) XSound.BLOCK_NOTE_BLOCK_PLING.play(it)
                }
            }
        }, 20L, 20L).bindWith(this)
    }

    private fun startLootSpawner()
    {
        if (!hasFinishRegion) return
        val startCenterX = assignedSpawns.values.map { it.x }.average().takeIf { it.isFinite() } ?: return
        val startCenterZ = assignedSpawns.values.map { it.z }.average().takeIf { it.isFinite() } ?: return
        val finishCenterX = (finishMinX + finishMaxX) / 2.0
        val finishCenterZ = (finishMinZ + finishMaxZ) / 2.0

        Schedulers.sync().runRepeating({ task ->
            if (gameEnded.get())
            {
                task.close()
                return@runRepeating
            }
            // Soft cap on item entities so the world doesn't get clogged
            val itemCount = game.arenaWorld.entities.count { it is org.bukkit.entity.Item }
            if (itemCount >= 24) return@runRepeating

            val t = ThreadLocalRandom.current().nextDouble(0.15, 0.85)
            val jitterX = ThreadLocalRandom.current().nextDouble(-2.0, 2.0)
            val jitterZ = ThreadLocalRandom.current().nextDouble(-2.0, 2.0)
            val x = startCenterX + (finishCenterX - startCenterX) * t + jitterX
            val z = startCenterZ + (finishCenterZ - startCenterZ) * t + jitterZ
            val loc = Location(game.arenaWorld, x, finishY.toDouble() + 1.0, z)
            game.arenaWorld.dropItemNaturally(loc, RlglLootTable.roll())
        }, RlglConstants.LOOT_SPAWN_INTERVAL_TICKS, RlglConstants.LOOT_SPAWN_INTERVAL_TICKS).bindWith(this)
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
