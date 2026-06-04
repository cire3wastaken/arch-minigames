package gg.tropic.practice.games

import com.cryptomorin.xseries.XMaterial
import com.cryptomorin.xseries.XSound
import gg.scala.aware.AwareBuilder
import gg.scala.aware.codec.codecs.interpretation.AwareMessageCodec
import gg.scala.aware.message.AwareMessage
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.playerstatus.PlayerStatusPreUpdateEvent
import gg.scala.commons.spatial.toPosition
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.util.QuickAccess
import gg.scala.lemon.util.QuickAccess.username
import gg.tropic.game.extensions.cosmetics.CosmeticLocalConfig
import gg.tropic.game.extensions.cosmetics.CosmeticRegistry
import gg.tropic.game.extensions.cosmetics.deathcry.DeathCry
import gg.tropic.game.extensions.cosmetics.deathcry.DeathCryCosmeticCategory
import gg.tropic.game.extensions.cosmetics.killeffects.KillEffectCosmeticCategory
import gg.tropic.game.extensions.cosmetics.killeffects.cosmetics.KillEffect
import gg.tropic.game.extensions.cosmetics.killeffects.cosmetics.LightningKillEffect
import gg.tropic.game.extensions.feature.event.LevelPrefixSelectEvent
import gg.tropic.practice.PracticeGame
import gg.tropic.practice.games.features.FireballFeature
import gg.tropic.practice.games.player.SpectatingPlayerProvider
import gg.tropic.practice.games.tasks.lifecycle.PlayerRespawnTask.Companion.startDelayedRespawn
import gg.tropic.practice.games.tasks.lifecycle.RoundStartTask.Companion.startNewRound
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.kit.feature.GameLifecycle
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.event.PlayerMiniGameDisconnectMidGameEvent
import gg.tropic.practice.minigame.event.PlayerMiniGameQuitWhileStartingEvent
import gg.tropic.practice.minigame.event.functionality.MiniGameDestroyBedEvent
import gg.tropic.practice.minigame.event.functionality.MiniGamePlayerDeathEvent
import gg.tropic.practice.extensions.ownedBy
import gg.tropic.practice.statistics.StatisticService
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIds
import gg.tropic.practice.statistics.statisticWrite
import gg.tropic.practice.suffixWhenDev
import gg.tropic.practice.extensions.BlastProtectionUtil
import gg.tropic.practice.extensions.toRBTeamSide
import gg.tropic.practice.games.damage.DeathMessageStrategy
import gg.tropic.practice.games.damage.EliminationCause
import gg.tropic.practice.games.damage.PlayerDamageTracker
import gg.tropic.practice.games.damage.getEliminationDetails
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.strategies.PlayerLocationStrategy
import gg.tropic.practice.ugc.HostedWorldInstanceService
import gg.tropic.practice.ugc.instance.HostedWorldInstanceLifecycleController
import gg.tropic.practice.ugc.toHostedWorld
import gg.tropic.practice.versioned.LegacySystemsService
import gg.tropic.practice.versioned.Versioned
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.nametag.NametagHandler
import net.evilblock.cubed.reboot.event.ServerShutdownEvent
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.ServerVersion
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.bukkit.Constants.HEART_SYMBOL
import net.evilblock.cubed.util.bukkit.EventUtils
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.bukkit.Tasks
import net.evilblock.cubed.visibility.VisibilityHandler
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.entity.*
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/**
 * @author GrowlyX
 * @since 8/4/2022
 */
@Service
object GameService
{
    @Inject
    lateinit var plugin: PracticeGame

    @Inject
    lateinit var audiences: BukkitAudiences

    val campProtectionTerminables = mutableMapOf<UUID, CompositeTerminable>()
    var isShallHostHostedWorlds = false
    private fun shallHostHostedWorlds()
    {
        isShallHostHostedWorlds = true
    }

    private val communicationLayer by lazy {
        AwareBuilder
            .of<AwareMessage>("practice-matchtermination".suffixWhenDev())
            .codec(AwareMessageCodec)
            .logger(plugin.logger)
            .build()
    }

    val gameMappings = ConcurrentHashMap<UUID, GameImpl>()

    val spectatorPlayers = ConcurrentHashMap.newKeySet<UUID>()
    val lightSpectatorPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun isSpectating(player: Player) = player.uniqueId in spectatorPlayers
    fun isLightSpectating(player: Player) = player.uniqueId in lightSpectatorPlayers

    val playerToGameMappings = Object2ReferenceOpenHashMap<UUID, GameImpl>(Bukkit.getMaxPlayers(), 0.75F)
    val spectatorToGameMappings = Object2ReferenceOpenHashMap<UUID, GameImpl>(Bukkit.getMaxPlayers(), 0.75F)

    fun Player.killedBy(): Entity?
    {
        if (
            lastDamageCause != null &&
            !lastDamageCause.isCancelled &&
            lastDamageCause is EntityDamageByEntityEvent
        )
        {
            val damager = (lastDamageCause as EntityDamageByEntityEvent).damager

            if (damager is Projectile)
            {
                val shooter = damager.shooter

                if (shooter != null)
                {
                    return shooter as Entity
                }
            }

            return damager
        }

        return null
    }

    fun runFinalDeathEffectsFor(
        killer: Player, killed: Player, game: GameImpl,
        invasive: Boolean
    )
    {
        val killerCosmetic = CosmeticRegistry
            .getAllEquipped(
                KillEffectCosmeticCategory,
                killer
            )
            .randomOrNull()
            ?: LightningKillEffect

        val killEffectCosmetic = killerCosmetic as KillEffect
        killEffectCosmetic.applyTo(
            game.toBukkitPlayers().filterNotNull(),
            killer, killed
        )

        val targetDeathCry = CosmeticRegistry
            .getAllEquipped(
                DeathCryCosmeticCategory,
                killed
            )
            .randomOrNull()

        if (targetDeathCry != null)
        {
            (targetDeathCry as DeathCry).play(killed)
        }

        val configuration = killEffectCosmetic.serveConfiguration(killer)
        if (configuration.flight != false && invasive)
        {
            killer.allowFlight = true
            killer.isFlying = true
        }

        if (configuration.clearInventory != false && invasive)
        {
            game.takeSnapshot(killer)

            killer.inventory.clear()
            killer.updateInventory()
        }
    }

    fun Player.gracefullyRemoveFromGame(event: GameRemovalEvent, cause: EliminationCause)
    {
        val game = byPlayer(this)
            ?: return kotlin.run {
                event.drops.clear()
            }

        game.getNullableTeam(this)
            ?: return

        if (!game.state(GameState.Playing))
        {
            return
        }

        if (
            isLightSpectating(this) ||
            isSpectating(this)
        )
        {
            return
        }

        game.takeSnapshot(this)

        health = maxHealth
        foodLevel = 20
        saturation = 20.0F

        scoreboard
            .getObjective(game.heartsInTablistTeam)
            ?.apply {
                displaySlot = null
            }

        scoreboard
            .getObjective(game.heartsBelowNametagTeam)
            ?.apply {
                displaySlot = null
            }

        campProtectionTerminables.remove(uniqueId)?.closeAndReportException()

        if (event.shouldRespawn)
        {
            spigot().respawn()
        }

        spectatorPlayers += uniqueId

        if (game.miniGameLifecycle == null)
        {
            if (game.flag(FeatureFlag.DoNotDropInventoryOnDeath))
            {
                event.drops.clear()
            } else
            {
                event.drops.removeIf { stack ->
                    stack.type == XMaterial.POTION.parseMaterial() ||
                        stack.type == XMaterial.BOWL.parseMaterial()
                }
            }
        }

        val team = game.getNullableTeam(this)
            ?: return

        val noAlive = if (team.players.size > 1)
            team.nonSpectators().isEmpty() else true

        if (!noAlive)
        {
            game.toBukkitPlayers()
                .filterNotNull()
                .forEach { other ->
                    VisibilityHandler.update(other)
                    NametagHandler.reloadPlayer(this, other)
                }
        }

        // Use the damage tracking system to determine elimination cause and context
        val (eliminationCause, eliminatedBy) = getEliminationDetails(cause)
        val killerPlayer = if (game.robot()) null else (event.killerPlayerOverride ?: killedBy())
        val killer = if (game.robot())
        {
            game.robotSide()
        } else
        {
            // If we have context from damage tracker, try to find that player
            val contextPlayer = if (eliminatedBy != null)
            {
                Bukkit.getPlayer(eliminatedBy)
            } else null

            val actualKiller = contextPlayer ?: killerPlayer
            if (actualKiller !is Player || actualKiller == this)
            {
                if (game.teams.size > 2) null else game.getOpponent(this)
            } else
            {
                game.getNullableTeam(actualKiller)
            }
        }

        // Determine the final killer player for statistics (prefer context player)
        val finalKillerPlayer = if (!game.robot())
        {
            if (eliminatedBy != null)
            {
                Bukkit.getPlayer(eliminatedBy) ?: killerPlayer
            } else
            {
                killerPlayer
            }
        } else null

        // Format team names based on red/blue teams flag
        // Only set killerName if we have a valid killer - don't use "???" fallback
        val actualKillerName = finalKillerPlayer?.name ?: eliminatedBy?.let { Bukkit.getPlayer(it)?.name }
        val killerName = if (actualKillerName != null) {
            if (game.flag(FeatureFlag.RedBlueTeams))
                killer?.teamIdentifier?.toRBTeamSide()?.format(actualKillerName)
                    ?: "${CC.GREEN}$actualKillerName"
            else "${CC.GREEN}$actualKillerName"
        } else null

        val killedName = if (game.flag(FeatureFlag.RedBlueTeams))
            team.teamIdentifier.toRBTeamSide().format(name)
        else "${CC.RED}$name"

        // Generate death message using the elimination cause system
        val deathMessage = DeathMessageStrategy.generate(
            killedBy = finalKillerPlayer as? Player,
            killedDisplayName = killedName,
            killedByDisplayName = killerName,
            eliminationCause = eliminationCause,
            alternativeDeath = event.alternativeDeath
        )

        // Handle killer effects and statistics
        if (finalKillerPlayer is Player && !game.robot())
        {
            if (!game.expectationModel.isPrivateGame && game.expectationModel.queueType != null)
            {
                StatisticService
                    .update(finalKillerPlayer.uniqueId) {
                        statisticWrite(
                            statisticIds {
                                if (game.miniGameLifecycle != null)
                                {
                                    kits(game.minigameType())
                                } else
                                {
                                    globalKit()
                                }
                                kits(game.kit)
                                types(TrackedKitStatistic.Kills)
                                globalQueueType()
                                queueTypes(game.expectationModel.queueType!!)
                            }
                        ) {
                            add(1)
                        }
                    }
            }

            finalKillerPlayer.playSound(
                finalKillerPlayer.location,
                XSound.BLOCK_NOTE_BLOCK_PLING.parseSound(),
                1.0f,
                3.0f
            )

            audiences.player(finalKillerPlayer).apply {
                sendActionBar(Component.text {
                    it.append(
                        Component
                            .text("KILL! ")
                            .color(NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD)
                    )

                    it.append(
                        Component
                            .text(this@gracefullyRemoveFromGame.name)
                            .color(NamedTextColor.YELLOW)
                    )
                })
            }
        }

        // Handle victim death statistics
        if (!game.robot() && !game.expectationModel.isPrivateGame && game.expectationModel.queueType != null)
        {
            StatisticService
                .update(uniqueId) {
                    statisticWrite(
                        statisticIds {
                            if (game.miniGameLifecycle != null)
                            {
                                kits(game.minigameType())
                            } else
                            {
                                globalKit()
                            }
                            kits(game.kit)
                            globalQueueType()
                            types(TrackedKitStatistic.Deaths)
                            queueTypes(game.expectationModel.queueType!!)
                        }
                    ) {
                        add(1)
                    }
                }
        }

        // Clean up damage tracking data for the eliminated player
        PlayerDamageTracker.clearPlayer(this.uniqueId)

        // Handle different game lifecycles
        when (game.lifecycle())
        {
            GameLifecycle.MiniGame ->
            {
                Bukkit.getPluginManager().callEvent(MiniGamePlayerDeathEvent(
                    player = this,
                    eliminationCause = eliminationCause,
                    game = game.miniGameLifecycle!!,
                    killer = finalKillerPlayer,
                    drops = event.drops
                ))
            }

            GameLifecycle.SoulBound ->
            {
                if (!game.robot())
                {
                    if (finalKillerPlayer is Player)
                    {
                        runFinalDeathEffectsFor(
                            finalKillerPlayer, this, game,
                            invasive = game.allNonSpectators()
                                .filter { it.uniqueId != uniqueId }
                                .size == 1
                        )
                    }
                }

                game.sendMessage(deathMessage)

                if (noAlive)
                {
                    game.complete(killer)
                }
                else if (game.isFreeForAll && team.nonSpectators().size <= 1)
                {
                    game.complete(team)
                }
            }

            GameLifecycle.RoundBound ->
            {
                if (
                    (game.flag(FeatureFlag.StartNewRoundOnDeath)) ||
                    (event.alternativeDeath && game.flag(FeatureFlag.StartNewRoundOnPortalEnter))
                )
                {
                    val requiredRounds = game
                        .flagMetaData(FeatureFlag.RoundsRequiredToCompleteGame, "value")
                        ?.toIntOrNull() ?: 2

                    val opponentWinner = game.getOpponent(team)
                    game.sendMessage(deathMessage)
                    if (opponentWinner.gameLifecycleArbitraryObjectiveProgress + 1 >= requiredRounds)
                    {
                        game.complete(opponentWinner)
                        return
                    }

                    game.multiRoundGame.roundNumber += 1
                    Tasks.delayed(1L) {
                        game.startNewRound(opponentWinner)
                    }
                    return
                }

                game.sendMessage(deathMessage)
                if (game.flag(FeatureFlag.SpectateAfterDeath))
                {
                    game.startDelayedRespawn(this)
                    return
                }

                game.prepareForNewLife(this, event.volatileState)
            }

            GameLifecycle.ObjectiveBound ->
            {
                game.sendMessage(deathMessage)

                if (game.flag(FeatureFlag.SpectateAfterDeath))
                {
                    game.startDelayedRespawn(this)
                    return
                }

                game.prepareForNewLife(this, event.volatileState)
            }

            GameLifecycle.ObjectivePlusSoulBound ->
            {
                val opposingTeam = game.getOpponent(team)
                if (game.gameLifecycleObjectiveMet(opposingTeam))
                {
                    if (finalKillerPlayer is Player)
                    {
                        runFinalDeathEffectsFor(finalKillerPlayer, this, game, noAlive)
                    }

                    game.sendMessage("$deathMessage ${CC.B_AQUA}FINAL KILL!")

                    if (noAlive)
                    {
                        game.complete(killer)
                    }
                    return
                }

                game.sendMessage(deathMessage)
                if (game.flag(FeatureFlag.SpectateAfterDeath))
                {
                    game.startDelayedRespawn(this)
                    return
                }

                game.prepareForNewLife(this, event.volatileState)
            }
        }
    }

    @Configure
    fun configure()
    {
        CosmeticLocalConfig.enableCosmeticResources = false
        SpectatingPlayerProvider.spectating = ::isSpectating

        plugin.registerListener(FireballFeature())

        Events
            .subscribe(EntityDamageByEntityEvent::class.java)
            .filter {
                !it.isCancelled &&
                    it.entity !is Player
                    && it.damager is Player
                    && byPlayer(it.damager as Player) != null
            }
            .handler { event ->
                if (isSpectating(event.damager as Player))
                {
                    event.isCancelled = true
                    return@handler
                }

                val game = byPlayer(event.damager as Player)
                    ?: return@handler

                val teamOfDamager = game
                    .getNullableTeam(event.damager as Player)
                    ?.teamIdentifier

                val ownedByTeam = event.entity.ownedBy()
                if (ownedByTeam == teamOfDamager)
                {
                    event.isCancelled = true
                    return@handler
                }
            }

        Events
            .subscribe(EntityTargetLivingEntityEvent::class.java)
            .filter { event ->
                !event.isCancelled &&
                    event.target is Player &&
                    event.entityType != EntityType.PLAYER &&
                    byPlayer(event.target as Player) != null
            }
            .handler { event ->
                val game = byPlayer(event.target as Player)
                val targetPlayer = event.target as Player

                if (isSpectating(targetPlayer))
                {
                    event.isCancelled = true
                    return@handler
                }

                val teamOfTarget = game
                    ?.getNullableTeam(event.target as Player)
                    ?.teamIdentifier

                val ownedByTeam = event.entity.ownedBy()
                if (ownedByTeam == teamOfTarget)
                {
                    event.isCancelled = true
                    return@handler
                }
            }

        Events
            .subscribe(BlockFromToEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler { event ->
                val game = byWorld(event.block.world)
                    ?: return@handler

                val block = event.toBlock
                Schedulers.sync()
                    .runLater({
                        val formedBlock = block.type

                        if (formedBlock == XMaterial.OBSIDIAN.get()
                            || formedBlock == XMaterial.STONE.get()
                            || formedBlock == XMaterial.COBBLESTONE.get()
                        )
                        {
                            block.setMetadata(
                                "placed",
                                FixedMetadataValue(plugin, true)
                            )

                            game.placedBlocks += block.location.toVector()
                        }
                    }, 10L)
            }

        Events
            .subscribe(ServerShutdownEvent::class.java)
            .handler {
                HostedWorldInstanceService.worldInstances().forEach {
                    HostedWorldInstanceLifecycleController.unload(it)
                }

                gameMappings.values.forEach { game ->
                    game.complete(null, "Server rebooting")
                }
            }

        communicationLayer.listen("terminate") {
            val matchID = retrieve<UUID>("matchID")
            val terminator = retrieveNullable<UUID>("terminator")
            val reason = retrieveNullable<String>("reason")
                ?: "Ended by an administrator"
            val game = gameMappings[matchID]
                ?: return@listen

            game.complete(null, reason = reason)

            QuickAccess.sendGlobalBroadcast(
                "${CC.L_PURPLE}[P] ${CC.D_PURPLE}[${
                    ServerSync.getLocalGameServer().id
                }] ${CC.L_PURPLE}Match ${CC.WHITE}${
                    matchID.toString().substring(0..5)
                }${CC.L_PURPLE} was terminated by ${CC.B_WHITE}${
                    terminator?.username() ?: "Console"
                }${CC.L_PURPLE} for ${CC.WHITE}${
                    reason
                }${CC.L_PURPLE}.",
                permission = "practice.admin"
            )
        }

        communicationLayer.connect()
        isShallHostHostedWorlds = true

        fun Player.sendHealthHUD(damaged: Player)
        {
            audiences.player(this).apply {
                sendActionBar {
                    Component.text { text ->
                        text.append(
                            Component.text("${damaged.name} ")
                                .color(NamedTextColor.YELLOW)
                        )

                        val hearts = damaged.health / 2.0
                        val fullHearts = floor(hearts).toInt()

                        repeat(fullHearts) {
                            text.append(
                                Component.text(HEART_SYMBOL)
                                    .color(NamedTextColor.DARK_RED)
                            )
                        }

                        if (hearts.toInt().toDouble() != hearts)
                        {
                            text.append(
                                Component.text(HEART_SYMBOL)
                                    .color(NamedTextColor.RED)
                            )
                        } else
                        {
                            text.append(
                                Component.text(HEART_SYMBOL)
                                    .color(NamedTextColor.GRAY)
                            )
                        }

                        val usedHearts = floor((damaged.maxHealth - damaged.health) / 2.0)
                        repeat(usedHearts.toInt()) {
                            text.append(
                                Component.text(HEART_SYMBOL)
                                    .color(NamedTextColor.GRAY)
                            )
                        }
                    }
                }
            }
        }

        Events
            .subscribe(PlayerInteractEvent::class.java)
            .handler {
                if (isSpectating(it.player))
                {
                    if (it.clickedBlock != null)
                    {
                        it.isCancelled = true
                        return@handler
                    }
                }

                val game = byPlayer(it.player)
                    ?: return@handler

                if (!game.state(GameState.Playing))
                {
                    return@handler
                }

                if (
                    !it.player.isDead &&
                    it.player.itemInHand.type == XMaterial.MUSHROOM_STEW.parseMaterial() &&
                    it.player.health < 19.0
                )
                {
                    val newHealth = min(it.player.health + 7.0, 20.0)

                    it.player.health = newHealth
                    it.player.itemInHand.type = XMaterial.BOWL.parseMaterial()
                    it.player.updateInventory()
                }
            }

        fun overridePotionEffect(
            player: Player, effect: PotionEffect
        )
        {
            if (player.hasPotionEffect(effect.type))
            {
                player.removePotionEffect(effect.type)
            }

            player.addPotionEffect(effect)
        }

        Events
            .subscribe(PlayerItemConsumeEvent::class.java)
            .filter {
                !it.isCancelled &&
                    it.item.type == XMaterial.POTION.parseMaterial()
                    && byPlayer(it.player) != null
            }
            .handler {
                Tasks.sync {
                    if (it.player.itemInHand.type != XMaterial.GLASS_BOTTLE.parseMaterial())
                    {
                        return@sync
                    }

                    it.player.itemInHand = ItemStack(Material.AIR)
                    it.player.updateInventory()
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(ProjectileLaunchEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                val fishingHook = it.entity

                if (it.entityType == EntityType.FISHING_HOOK)
                {
                    fishingHook.velocity = fishingHook
                        .velocity.multiply(1.17)
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(ProjectileLaunchEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (it.entityType == EntityType.ARROW && it.entity.shooter is Player)
                {
                    val player = it.entity.shooter as Player
                    val game = byPlayerOrSpectator(player.uniqueId)
                        ?: return@handler

                    if (game.flag(FeatureFlag.ArrowRefund))
                    {
                        val lifeTerminable = player
                            .getMetadata("life")
                            .firstOrNull()
                            ?.value()?.let { meta -> meta as CompositeTerminable }
                            ?: return@handler

                        Schedulers
                            .sync()
                            .runLater(
                                {
                                    if (Bukkit.getPlayer(player.uniqueId) == null)
                                    {
                                        return@runLater
                                    }

                                    player.playSound(
                                        player.location,
                                        XSound.ENTITY_ITEM_PICKUP.parseSound(),
                                        1.0f, 1.0f
                                    )

                                    player.inventory.addItem(XMaterial.ARROW.parseItem())
                                    player.updateInventory()
                                },
                                game
                                    .flagMetaData(FeatureFlag.ArrowRefund, "time")
                                    ?.toLong() ?: 3,
                                TimeUnit.SECONDS
                            )
                            .bindWith(lifeTerminable)
                    }
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(CraftItemEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (isSpectating(it.whoClicked as Player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayerOrSpectator(it.whoClicked.uniqueId)
                    ?: return@handler

                if (!game.state(GameState.Playing))
                {
                    it.isCancelled = true
                    return@handler
                }

                it.isCancelled = !game.shouldAllowCrafting
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerItemConsumeEvent::class.java)
            .filter {
                it.item.hasItemMeta() && it.item.itemMeta.displayName.contains("Heal Apple")
            }
            .handler {
                it.player.health = it.player.maxHealth
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerItemConsumeEvent::class.java)
            .filter {
                it.item.hasItemMeta() && it.item.itemMeta.displayName.contains("Golden Head")
            }
            .handler {
                it.player.playSound(
                    it.player.location, XSound.ENTITY_GENERIC_EAT.parseSound(), 10f, 1f
                )

                it.player.removePotionEffect(PotionEffectType.ABSORPTION)
                it.player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.ABSORPTION,
                        120 * 20, 0, false, true
                    )
                )

                it.player.removePotionEffect(PotionEffectType.REGENERATION)
                it.player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.REGENERATION,
                        5 * 20, 1, false, true
                    )
                )

                it.player.removePotionEffect(PotionEffectType.SPEED)
                it.player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.SPEED,
                        10 * 20, 0, false, true
                    )
                )

                if (it.item.amount == 1)
                {
                    it.player.inventory.removeItem(it.item)
                } else
                {
                    it.item.amount -= 1
                }
                it.player.updateInventory()
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerTeleportEvent::class.java)
            .filter {
                it.from.world != it.to.world/* && it.cause != PlayerTeleportEvent.TeleportCause.PLUGIN*/
            }
            .handler {
                val expectedWorld = PlayerLocationStrategy
                    .findPlayerExpectedWorld(it.player)

                if (expectedWorld == null)
                {
                    it.isCancelled = true
                    it.player.sendMessage("${CC.RED}You have no expected world! You should not be on this server.")
                    return@handler
                }

                if (it.to.world != expectedWorld)
                {
                    it.isCancelled = true
                    it.player.sendMessage("${CC.RED}The server tried teleporting you to a world you do not have access to.")
                }
            }

        Events
            .subscribe(ProjectileHitEvent::class.java)
            .filter { it.entityType == EntityType.SNOWBALL && byWorld(it.entity.world) != null }
            .handler { event ->
                val location = event.entity.location.clone().add(0.0, -1.0, 0.0)
                if (location.block.type === XMaterial.SNOW_BLOCK.parseMaterial())
                {
                    location.block.type = Material.AIR
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerItemConsumeEvent::class.java)
            .filter {
                if (it.isCancelled) return@filter false

                val game = byPlayer(it.player)
                it.item.type == Material.GOLDEN_APPLE &&
                    game != null &&
                    game.miniGameLifecycle == null
            }
            .handler {
                overridePotionEffect(
                    it.player, PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 1)
                )

                overridePotionEffect(
                    it.player, PotionEffect(PotionEffectType.ABSORPTION, 120 * 20, 0)
                )
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerDropItemEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (isSpectating(it.player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayer(it.player)
                    ?: return@handler

                if (!game.ensurePlaying() || game.flag(FeatureFlag.PreventDropAnyItems))
                {
                    it.isCancelled = true
                    return@handler
                }

                if (game.miniGameLifecycle == null)
                {
                    if (it.itemDrop.itemStack.type.name.contains("SWORD"))
                    {
                        val amountOfSwordsInInventory = it.player.inventory
                            .count { stack -> stack != null && stack.type.name.contains("SWORD") }

                        if (amountOfSwordsInInventory <= 1)
                        {
                            it.isCancelled = true
                            it.player.sendMessage("${CC.RED}You cannot drop your sword!")
                        }
                    } else if (it.itemDrop.itemStack.type == Material.FIREBALL)
                    {
                        it.isCancelled = true
                        it.player.sendMessage("${CC.RED}You cannot drop fireballs in this match!")
                    }
                } else if (game.isMinMaxLevelRange)
                {
                    if (it.player.location.blockY <= game.minMaxLevelRange.first)
                    {
                        it.isCancelled = true
                        return@handler
                    }
                }

                if (it.itemDrop.itemStack.type == Material.GLASS_BOTTLE)
                {
                    Schedulers.sync().runLater({
                        runCatching {
                            it.itemDrop.remove()
                        }
                    }, 1L)
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerPickupItemEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (isSpectating(it.player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayerOrSpectator(it.player.uniqueId)
                    ?: return@handler

                if (!game.ensurePlaying())
                {
                    it.isCancelled = true
                    return@handler
                }

                if (it.item.type != EntityType.ARROW)
                {
                    return@handler
                }

                if (game.flag(FeatureFlag.ArrowRefund))
                {
                    val preventArrowPickups = game
                        .flagMetaData(FeatureFlag.ArrowRefund, "preventArrowPickups")
                        ?.toBooleanStrictOrNull()
                        ?: true

                    if (preventArrowPickups)
                    {
                        it.isCancelled = true
                    }
                }
            }
            .bindWith(plugin)

        if (ServerVersion.getVersion().isOlderThan(ServerVersion.v1_9))
        {
            LegacySystemsService().configure()
        }

        Events
            .subscribe(PlayerMoveEvent::class.java)
            .filter(EventUtils::hasPlayerMoved)
            .handler {
                val game = byPlayer(it.player)
                    ?: return@handler

                if (isLightSpectating(it.player))
                {
                    it.player.teleport(it.from)
                    campProtectionTerminables.remove(it.player.uniqueId)?.closeAndReportException()
                    return@handler
                }

                if (isSpectating(it.player))
                {
                    campProtectionTerminables.remove(it.player.uniqueId)?.closeAndReportException()
                    return@handler
                }

                if (game.ensurePlaying())
                {
                    if (
                        it.to.block.type == Material.ENDER_PORTAL &&
                        game.flag(FeatureFlag.StartNewRoundOnPortalEnter)
                    )
                    {
                        val metadata = it.to.block
                            .getMetadata("tropicportal")
                            .firstOrNull()
                            ?: return@handler

                        val enteredIntoTeamPortal = TeamIdentifier.ID[metadata.asString().uppercase()]
                        if (game.multiRoundGame.switchingRounds)
                        {
                            return@handler
                        }

                        val teamOfPlayer = game.getTeamOf(it.player)
                        if (teamOfPlayer.teamIdentifier == enteredIntoTeamPortal)
                        {
                            it.player.gracefullyRemoveFromGame(
                                GameRemovalEvent(
                                    drops = mutableListOf(),
                                    shouldRespawn = false
                                ),
                                cause = EliminationCause.UNKNOWN
                            )
                            return@handler
                        }

                        // TODO: this might be a bit odd
                        val opponent = game.getOpponent(it.player)
                        val firstPlayer = opponent.toBukkitPlayers().firstOrNull()
                            ?: return@handler

                        firstPlayer.gracefullyRemoveFromGame(
                            GameRemovalEvent(
                                drops = mutableListOf(),
                                shouldRespawn = false,
                                alternativeDeath = true
                            ),
                            cause = EliminationCause.UNKNOWN
                        )
                    }

                    if (
                        game.flag(FeatureFlag.DeathOnLiquidInteraction) &&
                        it.to.block.isLiquid
                    )
                    {
                        it.player.gracefullyRemoveFromGame(
                            event = GameRemovalEvent(
                                drops = mutableListOf(),
                                shouldRespawn = false
                            ),
                            cause = EliminationCause.DROWNING
                        )
                        return@handler
                    }

                    val yAxis = game.voidDamageMin
                        ?: game
                            .flagMetaData(
                                FeatureFlag.DeathBelowYAxis, "level"
                            )
                            ?.toIntOrNull()

                    if (yAxis != null && it.to.y < yAxis)
                    {
                        it.player.gracefullyRemoveFromGame(
                            event = GameRemovalEvent(
                                drops = mutableListOf(),
                                shouldRespawn = false
                            ),
                            cause = EliminationCause.VOID_DAMAGE
                        )
                        return@handler
                    }

                    val buildLimit = game
                        .flagMetaData(
                            FeatureFlag.BuildLimit,
                            key = "blocks"
                        )
                        ?.toIntOrNull()

                    if (buildLimit != null)
                    {
                        val maxBuildLimit = (game.map
                            .findSpawnLocationMatchingTeam(TeamIdentifier.A)?.y
                            ?: 0.0) + buildLimit

                        if (it.to.y > maxBuildLimit + 1)
                        {
                            if (campProtectionTerminables[it.player.uniqueId] != null)
                            {
                                return@handler
                            }

                            val terminable = CompositeTerminable.create()
                            terminable.with {
                                audiences.player(it.player).clearTitle()
                            }

                            Schedulers.async()
                                .runLater({
                                    var tick = 5
                                    var setting = false
                                    Schedulers
                                        .sync()
                                        .runRepeating({ _ ->
                                            setting = !setting
                                            it.player.playSound(
                                                it.player.location,
                                                XSound.BLOCK_NOTE_BLOCK_PLING.parseSound(),
                                                0.75f,
                                                if (!setting) 1.5f else 0.75f
                                            )
                                        }, 0L, 2L)
                                        .bindWith(terminable)

                                    Schedulers
                                        .sync()
                                        .runRepeating({ _ ->
                                            audiences.player(it.player).apply {
                                                showTitle(
                                                    Title.title(
                                                        Component
                                                            .text("CAMP PROTECTION")
                                                            .color(NamedTextColor.RED)
                                                            .decorate(TextDecoration.BOLD),
                                                        Component
                                                            .text("Return to the map!")
                                                            .color(NamedTextColor.GRAY),
                                                        Title.Times.times(
                                                            Duration.ofMillis(10L),
                                                            Duration.ofMillis(1500L),
                                                            Duration.ofMillis(10L)
                                                        )
                                                    )
                                                )
                                            }
                                        }, 0L, 20L)
                                        .bindWith(terminable)

                                    Schedulers.async()
                                        .runRepeating({ task ->
                                            if (tick == 0)
                                            {
                                                Schedulers
                                                    .sync()
                                                    .runRepeating({ _ ->
                                                        it.player.damage(20.0 / 3)
                                                    }, 0L, 20L)
                                                    .bindWith(terminable)
                                                task.closeAndReportException()
                                                return@runRepeating
                                            }

                                            it.player.sendMessage("${CC.RED}Return back to the map in ${tick}s or else you will start taking damage!")
                                            tick -= 1
                                        }, 0L, 20L)
                                        .bindWith(terminable)
                                }, 100L)
                                .bindWith(terminable)

                            campProtectionTerminables[it.player.uniqueId] = terminable
                        } else
                        {
                            campProtectionTerminables.remove(it.player.uniqueId)?.closeAndReportException()
                        }
                    }

                    return@handler
                }

                if (
                    game.state(GameState.Starting) &&
                    (game.flag(FeatureFlag.FrozenOnGameStart))
                )
                {
                    it.player.teleport(it.from)
                    return@handler
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(FoodLevelChangeEvent::class.java)
            .handler {
                val game = byPlayer(it.entity as Player)
                    ?: return@handler

                if (!game.ensurePlaying())
                {
                    it.isCancelled = true
                    return@handler
                }

                if (game.flag(FeatureFlag.DoNotTakeHunger))
                {
                    it.isCancelled = true
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerDeathEvent::class.java)
            .filter { byPlayer(it.entity) != null }
            .handler {
                it.deathMessage = null
                it.entity.gracefullyRemoveFromGame(
                    event = GameRemovalEvent(
                        drops = it.drops,
                        volatileState = true,
                        shouldRespawn = true
                    ),
                    cause = EliminationCause.UNKNOWN
                )
            }
            .bindWith(plugin)

        Events
            .subscribe(PotionSplashEvent::class.java)
            .handler {
                it.affectedEntities.removeIf { entity ->
                    if (entity !is Player)
                    {
                        return@removeIf false
                    }

                    isSpectating(entity)
                }

                val shooter = it.entity.shooter
                if (shooter is Player)
                {
                    val game = byPlayer(shooter)
                        ?: return@handler

                    val counter = game.counter(shooter)
                    val intensity = it.getIntensity(shooter)
                    val effect = it.potion.effects
                        .firstOrNull { effect -> effect.type == PotionEffectType.HEAL }
                        ?: return@handler

                    counter.increment("totalPots")
                    counter.increment(if (intensity <= 0.5) "missedPots" else "hitPots")

                    val amountHealed = (intensity * (4 shl effect.amplifier) + 0.5)
                    if (shooter.health + amountHealed > shooter.maxHealth)
                    {
                        counter.increment(
                            "wastedHeals",
                            (shooter.health + amountHealed) - shooter.maxHealth
                        )
                    }
                }
            }

        Events
            .subscribe(EntityRegainHealthEvent::class.java)
            .filter { it.entity is Player }
            .handler {
                val player = it.entity as Player
                val game = byPlayer(player)
                    ?: return@handler

                if (it.regainReason == EntityRegainHealthEvent.RegainReason.REGEN)
                {
                    if (game.flag(FeatureFlag.HardcoreHealing))
                    {
                        it.isCancelled = true
                    } else
                    {
                        game.counter(player)
                            .apply {
                                increment("healthRegained", it.amount)
                            }
                    }
                    return@handler
                }

                game.counter(player)
                    .apply {
                        increment("healthRegained", it.amount)
                    }
            }

        Events
            .subscribe(GameStartEvent::class.java)
            .handler { event ->
                if (event.game.miniGameLifecycle != null)
                {
                    if (event.game.miniGameLifecycle!!.configuration.maximumPlayersPerTeam == 1)
                    {
                        Schedulers
                            .async()
                            .runLater({
                                event.game.sendMessage(
                                    "${CC.B_RED}Cross Teaming is bannable.",
                                    "${CC.RED}Report any players who gain an unfair advantage to staff by running /report username."
                                )
                            }, 40L)
                            .bindWith(event.game)
                    }
                }
            }

        Events
            .subscribe(PlayerQuitEvent::class.java, EventPriority.MONITOR)
            .handler {
                PlayerDamageTracker.clearPlayer(it.player.uniqueId)
                campProtectionTerminables
                    .remove(it.player.uniqueId)
                    ?.closeAndReportException()

                spectatorPlayers -= it.player.uniqueId
                lightSpectatorPlayers -= it.player.uniqueId

                val spectator = bySpectator(it.player.uniqueId)
                    ?: return@handler

                spectator.expectedSpectators -= it.player.uniqueId
            }

        Events
            .subscribe(LevelPrefixSelectEvent::class.java)
            .handler { event ->
                val game = byPlayerOrSpectator(event.player.uniqueId)
                    ?: return@handler

                event.gamemode = game.minigameType()
            }

        Events
            .subscribe(EntityDamageByBlockEvent::class.java)
            .filter {
                it.entity is Player
            }
            .handler {
                val player = it.entity as Player
                val spectating = isSpectating(player)
                if (spectating)
                {
                    // Cancel straight up. We'll fix void teleportation later on.
                    it.isCancelled = true
                }

                val game = byPlayerOrSpectator(player.uniqueId)
                    ?: return@handler

                PlayerDamageTracker.recordDamage(it.entity as Player, it)

                if (it.cause != DamageCause.VOID)
                {
                    return@handler
                }

                fun teleport()
                {
                    (game.map
                        .findSpawnLocationMatchingSpec()
                        ?: game.map
                            .findSpawnLocationMatchingTeam(TeamIdentifier.A))
                        ?.toLocation(player.location.world)
                        ?.let { location -> player.teleport(location) }
                }

                if (spectating)
                {
                    it.isCancelled = true
                    teleport()
                    return@handler
                }

                if (!game.state(GameState.Playing))
                {
                    it.isCancelled = true

                    game.getNullableTeam(player)
                        ?: return@handler run {
                            teleport()
                        }

                    game.teleportToSpawnLocation(player)
                } else
                {
                    player.gracefullyRemoveFromGame(
                        event = GameRemovalEvent(
                            drops = player.inventory.filterNotNull().toMutableList(),
                            shouldRespawn = true
                        ),
                        cause = EliminationCause.VOID_DAMAGE
                    )
                }
            }

        Events
            .subscribe(PlayerRespawnEvent::class.java)
            .handler { event ->
                val game = byPlayerOrSpectator(event.player.uniqueId)
                    ?: return@handler

                event.respawnLocation = (game.map
                    .findSpawnLocationMatchingSpec()
                    ?: game.map
                        .findSpawnLocationMatchingTeam(TeamIdentifier.A))
                    ?.toLocation(event.player.location.world)
            }

        Schedulers
            .async()
            .runRepeating({ _ ->
                gameMappings.values.forEach {
                    if (it.state != GameState.Playing)
                    {
                        return@runRepeating
                    }

                    if (it.toBukkitPlayers().all { player -> player == null })
                    {
                        Tasks.sync {
                            kotlin.runCatching {
                                it.complete(null, reason = "Zero players alive in match")
                            }.onFailure(Throwable::printStackTrace)
                        }
                    }
                }
            }, 0L, 20L)

        Events
            .subscribe(PlayerQuitEvent::class.java, EventPriority.LOWEST)
            .handler {
                val game = byPlayer(it.player)
                    ?: return@handler

                if (game.ensurePlaying())
                {
                    val team = game.getNullableTeam(it.player)
                        ?: return@handler
                    val noAlive = if (team.players.size > 1)
                        team.nonSpectators().isEmpty() else true

                    game.takeSnapshot(it.player)

                    if (game.miniGameLifecycle == null)
                    {
                        val opponent = game.getOpponent(it.player)
                        game.expectationModel.players -= it.player.uniqueId
                        game.sendMessage(
                            "${CC.RED}${it.player.name}${CC.GRAY} left the game."
                        )

                        if (noAlive)
                        {
                            game.complete(opponent)
                        } else
                        {
                            team.players -= it.player.uniqueId

                            if (game.isFreeForAll && team.nonSpectators().size <= 1)
                            {
                                game.complete(team)
                            }
                        }
                    } else
                    {
                        val miniGame = game as AbstractMiniGameGameImpl<*>
                        val disconnect = PlayerMiniGameDisconnectMidGameEvent(game, it.player)
                        Bukkit.getPluginManager().callEvent(disconnect)

                        game.expectationModel.players -= it.player.uniqueId
                        if (miniGame.miniGameLifecycle!!.configuration.shouldBeAbleToReconnect && disconnect.eligibleForRejoin)
                        {
                            Schedulers
                                .async()
                                .run {
                                    miniGame.playerTracker.saveRejoinToken(it.player)
                                }
                        } else
                        {
                            team.players -= it.player.uniqueId
                        }
                    }
                } else
                {
                    if (
                        game.state(GameState.Starting) ||
                        game.state(GameState.Waiting)
                    )
                    {
                        if (game.miniGameLifecycle != null)
                        {
                            Bukkit.getPluginManager().callEvent(PlayerMiniGameQuitWhileStartingEvent(
                                game as AbstractMiniGameGameImpl<*>,
                                it.player
                            ))
                            return@handler
                        }

                        game.state = GameState.Completed
                        game.closeAndCleanup()
                    }
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(EntityDamageEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .filter { it.entity is Player }
            .handler {
                if (isSpectating(it.entity as Player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayer(it.entity as Player)
                    ?: return@handler

                if (!game.ensurePlaying())
                {
                    it.isCancelled = true
                    return@handler
                }

                if (it.cause == DamageCause.FALL && game.flag(FeatureFlag.DoNotTakeFallDamage))
                {
                    it.isCancelled = true
                    return@handler
                }

                PlayerDamageTracker.recordDamage(it.entity as Player, it)
            }
            .bindWith(plugin)

        Events
            .subscribe(EntityDamageByEntityEvent::class.java)
            .filter { !it.isCancelled && it.damager is FishHook && ((it.damager as FishHook).shooter) is Player && it.entity is Player }
            .handler { event ->
                val game = byPlayer(
                    event.entity as Player
                )
                    ?: return@handler

                val damagerGame = byPlayer(
                    ((event.damager as FishHook).shooter) as Player
                )
                    ?: return@handler

                if (damagerGame.expectation == game.expectation)
                {
                    val damagerTeam = game
                        .getNullableTeam(((event.damager as FishHook).shooter) as Player)

                    val team = game
                        .getNullableTeam(event.entity as Player)

                    if (damagerTeam != null && team != null &&
                        damagerTeam.teamIdentifier == team.teamIdentifier && !game.shouldAllowFriendlyFire)
                    {
                        event.isCancelled = true
                        return@handler
                    }
                }

                // Prevent fishing rod hits from degrading armor durability.
                // Snapshot each armor piece's durability and restore it on the next tick.
                // TODO: ensure this doesn't cancel actual armor durability from rod trick
                val victim = event.entity as Player
                val armorDurabilities = victim.inventory.armorContents
                    .map { it?.durability }

                Tasks.delayed(1L) {
                    if (Bukkit.getPlayer(victim.uniqueId) == null) return@delayed

                    val armorContents = victim.inventory.armorContents
                    armorDurabilities.forEachIndexed { index, durability ->
                        if (durability != null && armorContents[index] != null)
                        {
                            armorContents[index].durability = durability
                        }
                    }
                    victim.inventory.armorContents = armorContents
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(EntityDamageByEntityEvent::class.java)
            .filter { !it.isCancelled && it.damager is Arrow && ((it.damager as Arrow).shooter) is Player && it.entity is Player }
            .handler { event ->
                val game = byPlayer(
                    event.entity as Player
                )
                    ?: return@handler

                val damagerGame = byPlayer(
                    ((event.damager as Arrow).shooter) as Player
                )
                    ?: return@handler

                val arrowShooter = (event.damager as Arrow).shooter as Player
                val isSelfShot = arrowShooter.uniqueId == (event.entity as Player).uniqueId

                if (!isSelfShot && damagerGame.expectation == game.expectation)
                {
                    val damagerTeam = game
                        .getTeamOf(arrowShooter)

                    val team = game
                        .getTeamOf(event.entity as Player)

                    if (damagerTeam.teamIdentifier == team.teamIdentifier && !game.shouldAllowFriendlyFire)
                    {
                        event.isCancelled = true
                        return@handler
                    }
                }

                val entity = event.entity as Player
                val arrow = event.damager as Arrow

                if (arrow.shooter is Player)
                {
                    val shooter = arrow.shooter as Player
                    val minYOffsetForHeadShot = if (entity.isSneaking) 1.25 else 1.5

                    if (arrow.location.y >= entity.location.y + minYOffsetForHeadShot)
                    {
                        game.counter(shooter).increment("headshots")

                        val multiplier = game.kit
                            .featureConfig(FeatureFlag.HeadshotMultiplier, "multiplier")
                            .toDouble()

                        if (multiplier != 1.0)
                        {
                            event.damage *= multiplier
                            shooter.sendMessage("${CC.B_RED}HEADSHOT! ${CC.YELLOW}You've hit ${CC.GREEN}${entity.name}${CC.YELLOW} with ${CC.B_WHITE}${multiplier}x${CC.YELLOW} the damage.")
                        }
                    }

                    if (entity.name != shooter.name && event.damage != 0.0)
                    {
                        val health = ceil(entity.health - event.finalDamage) / 2.0

                        if (health > 0.0)
                        {
                            if (game.shouldSendHealthHUD)
                            {
                                shooter.sendHealthHUD(entity)
                            }
                            shooter.sendMessage("${CC.GREEN}${entity.name}${CC.YELLOW} is now at ${CC.RED}${
                                "%.2f".format(health.toFloat())
                            }${HEART_SYMBOL} HP${CC.YELLOW}!")
                        }
                    }
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(EntityDamageByEntityEvent::class.java)
            .filter {
                !it.isCancelled && it.entity is Player
            }
            .handler {
                if (
                    (it.damager is Player && isSpectating(it.damager as Player)) ||
                    isSpectating(it.entity as Player)
                )
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayerOrSpectator(it.entity.uniqueId)
                    ?: return@handler

                if (!game.ensurePlaying())
                {
                    it.isCancelled = true
                    return@handler
                }

                PlayerDamageTracker.recordDamage(it.entity as Player, it)

                if (it.damager !is Player)
                {
                    if (!game.ensurePlaying())
                    {
                        it.isCancelled = true
                    }

                    return@handler
                }

                it.damager.removeMetadata("spawn-protection", plugin)

                if (it.entity.hasMetadata("spawn-protection"))
                {
                    val value = it.entity.getMetadata("spawn-protection").first().asLong()
                    if (value > System.currentTimeMillis())
                    {
                        it.isCancelled = true
                        it.damager.sendMessage(
                            "${CC.GOLD}${it.entity.name}${CC.RED} has spawn protection!"
                        )
                        return@handler
                    }
                }

                val damagerGame = byPlayer(
                    it.damager as Player
                )
                    ?: return@handler

                val damagerTeam = game.getTeamOf(it.damager as Player)
                val team = game.getTeamOf(it.entity as Player)

                if (damagerGame.expectation == game.expectation && !game.shouldAllowFriendlyFire)
                {
                    if (damagerTeam.teamIdentifier == team.teamIdentifier)
                    {
                        it.isCancelled = true
                        it.damager.sendMessage(
                            "${CC.RED}You cannot damage your teammates!"
                        )
                        return@handler
                    }
                }

                val doNotTakeDamage = game
                    .flagMetaData(
                        FeatureFlag.DoNotTakeDamage, "doDamageTick"
                    )
                    ?.toBooleanStrictOrNull()

                if (game.flag(FeatureFlag.WinWhenNHitsReached))
                {
                    damagerTeam.gameLifecycleArbitraryObjectiveProgress += 1
                }

                damagerTeam.playerCombos.compute(
                    it.damager!!.uniqueId
                ) { _, previous ->
                    val computed = (previous ?: 0) + 1
                    val highestCombo = damagerTeam
                        .highestPlayerCombos[it.damager.uniqueId]

                    if (highestCombo == null || highestCombo < computed)
                    {
                        damagerTeam.highestPlayerCombos[it.damager.uniqueId] = computed
                    }

                    computed
                }

                if (game.flag(FeatureFlag.HeartsBelowNameTag) && game.shouldSendHealthHUD)
                {
                    (it.damager as Player).sendHealthHUD(it.entity as Player)
                }

                game
                    .counter(it.entity as Player)
                    .apply {
                        reset("combo")
                    }

                game
                    .counter(it.damager as Player)
                    .apply {
                        increment("totalHits")
                        increment("combo")

                        if (valueOf("combo") > valueOf("highestCombo"))
                        {
                            update("highestCombo", valueOf("combo"))
                        }
                    }

                team.playerCombos.remove(it.entity.uniqueId)

                val winWhenHitsReached = game
                    .flagMetaData(
                        FeatureFlag.WinWhenNHitsReached, "hits"
                    )
                    ?.toIntOrNull()

                if (
                    winWhenHitsReached != null &&
                    damagerTeam.gameLifecycleArbitraryObjectiveProgress >= winWhenHitsReached
                )
                {
                    (it.entity as Player).gracefullyRemoveFromGame(
                        GameRemovalEvent(
                            killerPlayerOverride = it.damager as Player,
                            drops = mutableListOf(),
                            shouldRespawn = false
                        ),
                        cause = EliminationCause.UNKNOWN
                    )
                }

                if (doNotTakeDamage != null)
                {
                    if (doNotTakeDamage)
                    {
                        it.damage = 0.0
                        return@handler
                    }

                    it.isCancelled = true
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(EntityExplodeEvent::class.java)
            .handler {
                val game = byWorld(it.entity.world)
                    ?: return@handler

                if (game.shouldExplodeAll)
                {
                    return@handler
                }

                if (!game.flag(FeatureFlag.ExplodePlacedBlocks))
                {
                    it.isCancelled = true
                    return@handler
                }

                val isFireball = it.entityType == EntityType.FIREBALL // Fireball explosion size
                val isTNT = it.entityType == EntityType.PRIMED_TNT // TNT or other explosions

                it.blockList()
                    .removeIf { block ->
                        // Always protect beds and non-placed blocks from explosions
                        if (block.type.name.contains("BED"))
                        {
                            return@removeIf true
                        }

                        // Handle other explosion types or fallback to original logic
                        if (game.miniGameLifecycle != null)
                        {
                            if (!block.hasMetadata("placed"))
                            {
                                return@removeIf true
                            }

                            // Use BlastProtectionUtil to check if block is protected by blast-proof glass
                            if (BlastProtectionUtil.isProtected(it.location, block, 0.3))
                            {
                                return@removeIf true
                            }
                        }

                        // Fireball-specific rules (3.0 explosion size)
                        if (isFireball)
                        {
                            if (game.miniGameLifecycle != null)
                            {
                                when (block.type)
                                {
                                    Material.WOOD, Material.LOG, Material.LOG_2, Material.WOOD_STEP,
                                    Material.WOOD_DOUBLE_STEP, Material.WOODEN_DOOR, Material.TRAP_DOOR,
                                    Material.FENCE, Material.FENCE_GATE, Material.WOOD_STAIRS,
                                    Material.BIRCH_WOOD_STAIRS, Material.SPRUCE_WOOD_STAIRS,
                                    Material.JUNGLE_WOOD_STAIRS, Material.ACACIA_STAIRS,
                                    Material.DARK_OAK_STAIRS, Material.WOOL ->
                                    {
                                        // Allow fireballs to break wood and wool
                                        return@removeIf false
                                    }

                                    else ->
                                    {
                                        // Fireballs cannot break other blocks
                                        return@removeIf true
                                    }
                                }
                            } else
                            {
                                when (block.type)
                                {
                                    Material.WOOD, Material.WOOL ->
                                    {
                                        // Allow fireballs to break wood and wool
                                        return@removeIf false
                                    }

                                    else ->
                                    {
                                        // Fireballs cannot break other blocks
                                        return@removeIf true
                                    }
                                }
                            }


                        }

                        // TNT-specific rules
                        if (isTNT)
                        {
                            if (game.miniGameLifecycle != null)
                            {
                                when (block.type)
                                {
                                    Material.ENDER_STONE, Material.WOOL,
                                    Material.WOOD, Material.LOG, Material.LOG_2, Material.WOOD_STEP,
                                    Material.WOOD_DOUBLE_STEP, Material.WOODEN_DOOR, Material.TRAP_DOOR,
                                    Material.FENCE, Material.FENCE_GATE, Material.WOOD_STAIRS,
                                    Material.BIRCH_WOOD_STAIRS, Material.SPRUCE_WOOD_STAIRS,
                                    Material.JUNGLE_WOOD_STAIRS, Material.ACACIA_STAIRS,
                                    Material.DARK_OAK_STAIRS,
                                    Material.CLAY, Material.STAINED_CLAY, Material.HARD_CLAY ->
                                    {
                                        // Allow TNT to break endstone, wool, wood, and clay
                                        return@removeIf false
                                    }

                                    else ->
                                    {
                                        // TNT cannot break other blocks
                                        return@removeIf true
                                    }
                                }
                            } else
                            {
                                when (block.type)
                                {
                                    Material.ENDER_STONE, Material.WOOL,
                                    Material.WOOD ->
                                    {
                                        // Allow TNT to break endstone, wool, wood, and clay
                                        return@removeIf false
                                    }

                                    else ->
                                    {
                                        // TNT cannot break other blocks
                                        return@removeIf true
                                    }
                                }
                            }
                        }

                        if (game.flag(FeatureFlag.BreakSpecificBlockTypes))
                        {
                            if (game.kit.allowedBlockTypeMappings.isEmpty)
                            {
                                return@removeIf false
                            }

                            return@removeIf game.kit.allowedBlockTypeMappings.get()[block.type]?.toByte() != block.data
                        }

                        return@removeIf true
                    }
            }
            .bindWith(plugin)

        Events
            .subscribe(ItemSpawnEvent::class.java)
            .filter {
                it.entity.itemStack.type.name.endsWith("BED") &&
                    byWorld(it.entity.world) != null
            }
            .handler {
                it.isCancelled = true
            }

        fun refundBlocks(block: Block, game: GameImpl)
        {
            if (
                game.flag(FeatureFlag.BlockRefund) &&
                block.hasMetadata("placed") &&
                game.state(GameState.Playing)
            )
            {
                block.getMetadata("placed").first().let { meta ->
                    val player = plugin.server
                        .getPlayer(meta.value() as UUID)
                        ?: return@let

                    player.inventory.addItem(ItemStack(block.type, 1, block.data.toShort()))
                }
            }
        }

        // Idk how you would work around this, you NEED polar to act on the event first
        // don't set priority for now
        Events
            .subscribe(BlockBreakEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (isSpectating(it.player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayer(it.player)
                    ?: return@handler

                fun handleDropCheck()
                {
                    if (game.flag(FeatureFlag.DoNotDropBrokenBlocks))
                    {
                        it.isCancelled = true
                        it.block.type = Material.AIR
                    }
                }

                if (!game.ensurePlaying())
                {
                    it.isCancelled = true
                    return@handler
                }

                if (game.spawnProtectionZonesUnplaced.isNotEmpty() && !it.block.hasMetadata("placed"))
                {
                    val position = it.block.location.toPosition()
                    if (game.spawnProtectionZonesUnplaced.any { bounds -> bounds.contains(position) })
                    {
                        it.isCancelled = true
                        return@handler
                    }
                }

                if (game.spawnProtectionZones.isNotEmpty())
                {
                    val position = it.block.location.toPosition()
                    if (game.spawnProtectionZones.any { bounds -> bounds.contains(position) })
                    {
                        it.isCancelled = true
                        return@handler
                    }
                }

                if (game.flag(FeatureFlag.BreakAllBlocks))
                {
                    refundBlocks(it.block, game)
                    handleDropCheck()
                    return@handler
                }

                if (
                    game.flag(FeatureFlag.BreakPlacedBlocks) &&
                    it.block.hasMetadata("placed")
                )
                {
                    refundBlocks(it.block, game)
                    handleDropCheck()
                    return@handler
                }

                if (
                    it.block.type.name.contains("BED") &&
                    (game.lifecycle() == GameLifecycle.ObjectivePlusSoulBound ||
                        game.deathDecisionLifecycle() == GameLifecycle.ObjectivePlusSoulBound)
                )
                {
                    var tropicBedSide: TeamIdentifier? = null
                    if (game.lifecycle() == GameLifecycle.MiniGame)
                    {
                        tropicBedSide = it.block.ownedBy()
                    } else
                    {
                        val nearestSpawnLocation = game.map.findSpawnLocations()
                            .minByOrNull { spawn ->
                                spawn.position
                                    .toLocation(it.block.world)
                                    .distanceSquared(it.block.location)
                            }

                        tropicBedSide = nearestSpawnLocation?.id
                            ?.let { id -> TeamIdentifier(id.uppercase()) }
                    }

                    val currentTeam = game.getTeamOf(it.player)
                    val currentSide = currentTeam.teamIdentifier

                    if (tropicBedSide == null)
                    {
                        it.isCancelled = true
                        return@handler
                    }

                    if (tropicBedSide == currentSide)
                    {
                        it.player.sendMessage("${CC.RED}You cannot break your own bed!")
                        it.isCancelled = true
                        return@handler
                    }

                    if (game.lifecycle() == GameLifecycle.MiniGame)
                    {
                        Bukkit.getPluginManager().callEvent(MiniGameDestroyBedEvent(
                            destroyedBy = it.player,
                            teamDestroyed = tropicBedSide,
                            event = it,
                            game = game.miniGameLifecycle!!
                        ))
                        return@handler
                    }

                    val opponent = game.getOpponent(currentTeam)

                    if (currentTeam.gameLifecycleArbitraryObjectiveProgress != 1)
                    {
                        currentTeam.gameLifecycleArbitraryObjectiveProgress = 1
                        game.playSound(XSound.ENTITY_ENDER_DRAGON_GROWL.parseSound()!!, 1.0f)
                        game.sendMessage(
                            "",
                            "${CC.B_WHITE}BED DESTRUCTION ${CC.GRAY}${Constants.DOUBLE_ARROW_RIGHT}${CC.WHITE} ${
                                "${
                                    opponent.teamIdentifier.toRBTeamSide()
                                        .let { side -> "${side.color}${side.display}" }
                                } Bed${CC.GRAY} was destroyed by ${
                                    "${currentTeam.teamIdentifier.toRBTeamSide().color}${it.player.name}${CC.GRAY}!"
                                }"
                            }",
                            ""
                        )

                        opponent.toBukkitPlayers().filterNotNull()
                            .forEach { player ->
                                val audience = audiences.player(player)
                                audience.showTitle(
                                    Title.title(
                                        Component.text("BED DESTROYED!")
                                            .color(NamedTextColor.RED)
                                            .decorate(TextDecoration.BOLD),
                                        Component.text("You will no longer respawn!")
                                    )
                                )
                            }

                        it.block.breakNaturally()
                        it.isCancelled = true
                        return@handler
                    }
                }

                if (game.kit.allowedBlockTypeMappings.isPresent)
                {
                    if (game.kit.allowedBlockTypeMappings.get()[it.block.type]?.toByte() == it.block.data)
                    {
                        if (it.block.type == Material.SNOW_BLOCK)
                        {
                            it.isCancelled = true
                            it.block.type = Material.AIR

                            it.player.inventory.addItem(
                                ItemBuilder
                                    .of(XMaterial.SNOWBALL)
                                    .amount(4)
                                    .build()
                            )
                            it.player.updateInventory()

                            handleDropCheck()
                            return@handler
                        }

                        handleDropCheck()
                        return@handler
                    }
                }

                it.isCancelled = true
            }
            .bindWith(plugin)

        val validBlockPlace = listOf(
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST
        )

        Events
            .subscribe(PlayerDeathEvent::class.java)
            .handler { deathEvent ->
                deathEvent.deathMessage = null
            }

        // same issue as with the blockbreakevent, we need to ensure polar handles event first
        // don't set priority for now
        Events
            .subscribe(BlockPlaceEvent::class.java)
            .filter {
                !it.isCancelled
            }
            .handler {
                if (isSpectating(it.player))
                {
                    it.isCancelled = true
                    return@handler
                }

                val game = byPlayer(it.player)
                    ?: return@handler

                if (isLightSpectating(it.player))
                {
                    it.isCancelled = true
                    it.player.sendMessage("${CC.RED}You cannot do this yet!")
                    return@handler
                }

                if (!game.ensurePlaying() && it.block.type != Material.TNT)
                {
                    it.isCancelled = true
                    return@handler
                }

                if (!game.flag(FeatureFlag.PlaceBlocks))
                {
                    it.isCancelled = true
                    return@handler
                }

                if (game.spawnProtectionZones.isNotEmpty())
                {
                    val position = it.blockPlaced.location.toPosition()
                    if (game.spawnProtectionZones.any { bounds -> bounds.contains(position) })
                    {
                        it.isCancelled = true
                        return@handler
                    }
                }

                val buildLimit = game
                    .flagMetaData(
                        FeatureFlag.BuildLimit,
                        key = "blocks"
                    )
                    ?.toIntOrNull()

                if (buildLimit != null)
                {
                    val maxBuildLimit = (game.map
                        .findSpawnLocationMatchingTeam(TeamIdentifier.A)?.y
                        ?: 0.0) + buildLimit

                    if (it.block.y > maxBuildLimit)
                    {
                        it.isCancelled = true
                        it.player.sendMessage(
                            "${CC.RED}You cannot build in this area!"
                        )
                        return@handler
                    }
                }

                val zone = game.map.findZoneContainingBlock(it.block)
                if (zone != null && zone.id.startsWith("restrict"))
                {
                    it.isCancelled = true
                    it.player.sendMessage(
                        "${CC.RED}You cannot build in this area!"
                    )
                    return@handler
                }

                if (!game.isMinIndependent)
                {
                    if (!game.isMinMaxLevelRange)
                    {
                        val levelRestrictions = game.map.findMapLevelRestrictions()

                        if (levelRestrictions != null)
                        {
                            if (it.block.y !in levelRestrictions.range)
                            {
                                if (
                                    !levelRestrictions.allowBuildOnBlockSideBlockFaces ||
                                    validBlockPlace
                                        .any { face ->
                                            it.blockPlaced.getRelative(face)
                                                .hasMetadata("placed")
                                        }
                                )
                                {
                                    it.isCancelled = true
                                    it.player.sendMessage(
                                        "${CC.RED}You cannot build in this area!"
                                    )
                                }
                                return@handler
                            }
                        }
                    } else if (it.block.y !in game.minMaxLevelRange)
                    {
                        it.isCancelled = true
                        it.player.sendMessage(
                            "${CC.RED}You cannot build in this area!"
                        )
                        return@handler
                    }

                    if (it.block.type == Material.TNT &&
                        (game.minigameType() == "bedwars" || game.miniGameLifecycle == null))
                    {
                        it.blockPlaced.type = Material.AIR

                        val tnt = it.block.location.world.spawn(
                            it.block.location.add(0.5, 0.0, 0.5),
                            TNTPrimed::class.java
                        )
                        tnt.fuseTicks = 40
                        setSource(tnt, it.player)
                        return@handler
                    }
                }

                it.blockPlaced.setMetadata(
                    "placed",
                    FixedMetadataValue(plugin, it.player.uniqueId)
                )

                if (game.flag(FeatureFlag.RemovePlacedBlocksOnRoundStart))
                {
                    game.placedBlocks += it.blockPlaced.location.toVector()
                }

                val blockExpiration = game
                    .flagMetaData(
                        FeatureFlag.ExpirePlacedBlocksAfterNSeconds,
                        "time"
                    )
                    ?.toIntOrNull()

                if (blockExpiration != null)
                {
                    val typeToReturn = ItemBuilder
                        .copyOf(it.itemInHand)
                        .amount(1)
                        .build()

                    Schedulers
                        .sync()
                        .runLater({
                            if (it.blockPlaced.hasMetadata("placed"))
                            {
                                val returnBlocks = game
                                    .flagMetaData(FeatureFlag.ExpirePlacedBlocksAfterNSeconds, "return")
                                    ?.toBooleanStrictOrNull()
                                    ?: false

                                if (returnBlocks)
                                {
                                    it.player.inventory.addItem(typeToReturn)
                                    it.player.updateInventory()
                                }

                                if (it.blockPlaced.location.block.type == it.blockPlaced.type)
                                    refundBlocks(it.blockPlaced, game)

                                it.blockPlaced.type = Material.AIR
                            }
                        }, blockExpiration * 20L)
                        .bindWith(game.multiRoundGame.terminable)
                }
            }
            .bindWith(plugin)

        Events
            .subscribe(PlayerStatusPreUpdateEvent::class.java)
            .handler { event ->
                event.player.toHostedWorld()?.apply {
                    event.playerStatus.activityDescription = "in a hosted world"
                    event.playerStatus.returnToServerGroup = providerType.lobbyGroup
                    return@handler
                }

                val game = byPlayerOrSpectator(event.player.uniqueId)
                    ?: return@handler run {
                        event.playerStatus.activityDescription = "in a minigame"
                        event.playerStatus.returnToServerGroup = "hub"
                    }

                if (isSpectating(event.player))
                {
                    event.playerStatus.activityDescription = "spectating a game"
                    event.playerStatus.returnToServerGroup = game.lobbyGroup()
                    return@handler
                }

                if (game.miniGameLifecycle != null)
                {
                    event.playerStatus.activityDescription = "in a ${
                        game.miniGameLifecycle!!.configuration.gameDescription
                    } game"
                    event.playerStatus.returnToServerGroup = game.lobbyGroup()
                    return@handler
                }

                event.playerStatus.activityDescription = "in a duel"
                event.playerStatus.returnToServerGroup = "miplobby"
            }
            .bindWith(plugin)
    }

    fun byPlayer(player: Player) =
        playerToGameMappings[player.uniqueId]

    fun byPlayer(player: UUID) =
        playerToGameMappings[player]

    fun byWorld(world: World) = gameMappings.values.firstOrNull { world.name == it.arenaWorld.name }

    fun bySpectator(player: UUID) =
        spectatorToGameMappings[player]

    fun byPlayerOrSpectator(player: UUID) =
        playerToGameMappings[player]
            ?: spectatorToGameMappings[player]

    fun iterativeByPlayerOrSpectator(player: UUID): GameImpl?
    {
        var spectatorMatch: GameImpl? = null
        for (game in gameMappings.values)
        {
            if (player in game.toPlayers()) return game
            if (spectatorMatch == null && player in game.expectedSpectators) spectatorMatch = game
        }
        return spectatorMatch
    }

    fun setSource(tnt: TNTPrimed, owner: Player)
    {
        Versioned.toProvider().getPlayerProvider().setTntSource(tnt, owner)
    }
}
