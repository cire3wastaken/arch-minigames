package mc.arch.minigames.hungergames.game

import com.cryptomorin.xseries.XMaterial
import gg.scala.lemon.handler.PlayerHandler
import gg.tropic.practice.extensions.resetAttributes
import gg.tropic.practice.games.GameReport
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.GameStartEvent
import gg.tropic.practice.minigame.AbstractMiniGameGameImpl
import gg.tropic.practice.minigame.MiniGameLifecycle
import gg.tropic.practice.minigame.MiniGameScoreboard
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import mc.arch.minigames.hungergames.HungerGamesGameConfiguration
import mc.arch.minigames.hungergames.HungerGamesTypeMetadata
import mc.arch.minigames.hungergames.privategames.HungerGamesPrivateGameSettings
import mc.arch.minigames.hungergames.game.events.*
import mc.arch.minigames.hungergames.game.resources.PlayerResources
import mc.arch.minigames.hungergames.lootpool.HGLootGenerator
import mc.arch.minigames.hungergames.lootpool.HGLootType
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import me.lucko.helper.Events
import me.lucko.helper.Helper
import me.lucko.helper.Schedulers
import me.lucko.helper.terminable.composite.CompositeTerminable
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Constants
import net.evilblock.cubed.util.time.TimeUtil
import net.kyori.adventure.platform.bukkit.BukkitAudiences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.lang.AutoCloseable
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * @author ArchMC
 */
class HungerGamesLifecycle(
    override val configuration: HungerGamesGameConfiguration,
    override val game: AbstractMiniGameGameImpl<HungerGamesGameConfiguration>,
    override val typeConfiguration: MiniGameTypeMetadata = HungerGamesTypeMetadata
) : MiniGameLifecycle<HungerGamesGameConfiguration>, CompositeTerminable
{
    val playerResources = ConcurrentHashMap<UUID, PlayerResources>()

    fun playerResourcesOf(player: Player) = playerResources.getOrPut(player.uniqueId) {
        PlayerResources(
            player.uniqueId,
            player.name,
            game.expectedSpectators.contains(player.uniqueId),
            PlayerHandler.find(player.uniqueId)
                ?.getColoredName(prefixIncluded = true)
                ?: player.name
        )
    }

    fun playerResourcesOf(player: UUID) = playerResources[player]

    // Game state
    var deathmatch = false
    var deathmatchStartedAt = 0L
    var deathmatchGracePeriod = 0
    var blitzStarInChest = false

    private val gracePeriodSeconds: Int = if (game.expectationModel.isPrivateGame)
    {
        game.expectationModel.privateGameSettings
            ?.getSetting(HungerGamesPrivateGameSettings.GRACE_PERIOD, HungerGamesPrivateGameSettings.DEFAULT_GRACE_PERIOD) ?: 0
    } else 0

    private val chestRefillSeconds: Int = if (game.expectationModel.isPrivateGame)
    {
        game.expectationModel.privateGameSettings
            ?.getSetting(HungerGamesPrivateGameSettings.CHEST_REFILL_TIMER, HungerGamesPrivateGameSettings.DEFAULT_CHEST_REFILL_TIMER) ?: 0
    } else 0

    private val naturalRegenEnabled: Boolean = if (game.expectationModel.isPrivateGame)
    {
        game.expectationModel.privateGameSettings
            ?.getSetting(HungerGamesPrivateGameSettings.NATURAL_REGEN, HungerGamesPrivateGameSettings.DEFAULT_NATURAL_REGEN) ?: false
    } else false

    // Glass cage spawn system
    val glassCages = mutableListOf<GlassCage>()
    val playerCageAssignments = ConcurrentHashMap<UUID, GlassCage>()

    override val events = listOf(
        ApplyKitsGameEvent(this@HungerGamesLifecycle),
        ReleaseBlitzStarGameEvent(this@HungerGamesLifecycle),
        ChestRefillGameEvent(this@HungerGamesLifecycle),
        DeathmatchGameEvent(this@HungerGamesLifecycle),
        GameEndGameEvent(this@HungerGamesLifecycle)
    )

    override val scoreboard: MiniGameScoreboard = HungerGamesScoreboard(game)

    override fun configure()
    {
        val audiences = BukkitAudiences.create(Helper.hostPlugin())
        val world = game.arenaWorld

        game.shouldAllowCrafting = true
        game.shouldContainIdentifiableTeams = false
        game.shouldBeMinMaxEligible = true

        // Build glass cages at all spawn locations
        game.map.findSpawnLocations()
            .filter { it.id != "spec" }
            .forEach { spawnMeta ->
                val location = spawnMeta.position.toLocation(world)
                val cage = GlassCage(location)
                cage.build()
                glassCages.add(cage)
            }

        // Win condition check + compass tracking
        Schedulers
            .async()
            .runRepeating({ task ->
                if (game.state != GameState.Playing)
                {
                    return@runRepeating
                }

                val teamsNotEliminated = game.allNonSpectators()
                if (teamsNotEliminated.size <= 1)
                {
                    task.closeAndReportException()

                    Schedulers
                        .sync()
                        .run {
                            game.complete(teamsNotEliminated.firstOrNull()?.let {
                                game.getNullableTeam(it)
                            })
                        }
                } else
                {
                    // Compass tracking
                    game.allNonSpectators().forEach { player ->
                        if (player.itemInHand.type == XMaterial.COMPASS.parseMaterial())
                        {
                            val nearestNonSpectator = game.allNonSpectators()
                                .filter { other -> other.uniqueId != player.uniqueId && other.world == player.world }
                                .minByOrNull { other -> other.location.distance(player.location) }
                                ?: return@forEach

                            player.compassTarget = nearestNonSpectator.location
                        } else
                        {
                            player.compassTarget = game.arenaWorld.spawnLocation
                        }
                    }

                    // Auto-trigger deathmatch message if few players left
                    if (!deathmatch && teamsNotEliminated.size <= 3)
                    {
                        val timeUntilNext = game.tracker.timeUntilNextEvent()
                        if (timeUntilNext > 45000 && game.tracker.nextEvent()?.description == "Deathmatch")
                        {
                            // Only auto-trigger if deathmatch is the next event and it's far away
                            Schedulers
                                .sync()
                                .run {
                                    game.sendMessage("${CC.YELLOW}Deathmatch starting early due to few players remaining!")
                                    events[3].execute()
                                }
                        }
                    }
                }
            }, 0L, 20L)
            .bindWith(game)

        // Kit action bar display during waiting/starting
        val updateHotBarTerminable = CompositeTerminable.create()
        updateHotBarTerminable.bindWith(game)
        updateHotBarTerminable.with {
            game.toBukkitPlayers().filterNotNull()
                .forEach {
                    audiences.player(it)
                        .sendActionBar(Component.empty())
                }
        }

        Schedulers
            .sync()
            .runRepeating({ _ ->
                for (player in game.toBukkitPlayers().filterNotNull())
                {
                    val profile = HungerGamesProfileService.find(player)
                        ?: continue

                    audiences.player(player)
                        .sendActionBar(Component.text { builder ->
                            builder.append(
                                Component
                                    .text("Selected kit: ")
                                    .color(NamedTextColor.YELLOW)
                            )

                            builder.append(
                                Component
                                    .text(profile.selectedKit?.replaceFirstChar { it.uppercase() } ?: "None")
                                    .color(NamedTextColor.GREEN)
                            )
                        })
                }
            }, 0L, 10L)
            .bindWith(updateHotBarTerminable)

        // Game start events
        Events
            .subscribe(GameStartEvent::class.java)
            .filter { it.game.expectation == game.expectation }
            .handler {
                updateHotBarTerminable.closeAndReportException()

                // Destroy all glass cages
                glassCages.forEach { it.destroy() }
                glassCages.clear()
                playerCageAssignments.clear()

                // Fall damage protection
                val fallInvincibilityTerminable = CompositeTerminable.create()
                fallInvincibilityTerminable.bindWith(game)
                Events
                    .subscribe(org.bukkit.event.entity.EntityDamageEvent::class.java)
                    .filter {
                        it.cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL &&
                            GameService.byWorld(it.entity.world) == game
                    }
                    .handler {
                        it.isCancelled = true
                    }
                    .bindWith(fallInvincibilityTerminable)

                Schedulers
                    .async()
                    .runLater({
                        fallInvincibilityTerminable.closeAndReportException()
                    }, 40L)
                    .bindWith(fallInvincibilityTerminable)

                // Give resistance and fill initial chests
                for (player in game.toBukkitPlayers().filterNotNull())
                {
                    player.resetAttributes(true)
                    player.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 60 * 20, 0, true, false))

                    // Give tracking compass
                    player.inventory.addItem(
                        net.evilblock.cubed.util.bukkit.ItemBuilder
                            .of(XMaterial.COMPASS)
                            .name("${CC.GREEN}Tracking Compass")
                            .addToLore("${CC.GRAY}Points to the nearest player!")
                            .build()
                    )
                    player.updateInventory()
                }

                // Fill initial chests
                val chests = mutableListOf<Chest>()
                for (chunk in world.loadedChunks)
                {
                    for (te in chunk.tileEntities)
                    {
                        if (te is Chest)
                        {
                            chests.add(te)
                        }
                    }
                }
                HGLootGenerator.fillChestsFromDataSync(chests, HGLootType.INITIAL)

                if (game.expectationModel.isPrivateGame)
                {
                    world.setGameRuleValue("naturalRegeneration", naturalRegenEnabled.toString())
                }

                if (gracePeriodSeconds > 0)
                {
                    val graceTerminable = CompositeTerminable.create()
                    graceTerminable.bindWith(game)

                    Events
                        .subscribe(org.bukkit.event.entity.EntityDamageByEntityEvent::class.java)
                        .filter { it.damager is Player && it.entity is Player }
                        .filter { GameService.byWorld(it.entity.world) == game }
                        .handler { it.isCancelled = true }
                        .bindWith(graceTerminable)

                    game.sendMessage("${CC.YELLOW}Grace period active — PvP is disabled for ${CC.GREEN}${gracePeriodSeconds}s${CC.YELLOW}.")

                    Schedulers
                        .sync()
                        .runLater({
                            graceTerminable.closeAndReportException()
                            game.sendMessage("${CC.RED}The grace period has ended — PvP is now enabled!")
                        }, gracePeriodSeconds * 20L)
                        .bindWith(game)
                }

                if (chestRefillSeconds > 0)
                {
                    Schedulers
                        .sync()
                        .runRepeating({ _ ->
                            val refillChests = mutableListOf<Chest>()
                            for (chunk in world.loadedChunks)
                            {
                                for (te in chunk.tileEntities)
                                {
                                    if (te is Chest)
                                    {
                                        te.inventory.clear()
                                        refillChests.add(te)
                                    }
                                }
                            }
                            HGLootGenerator.fillChestsFromDataSync(refillChests, HGLootType.REFILL)
                            game.sendMessage("${CC.YELLOW}All chests have been ${CC.GREEN}refilled${CC.YELLOW}!")
                            game.playSound(Sound.CHEST_OPEN, 1.0f)
                        }, chestRefillSeconds * 20L, chestRefillSeconds * 20L)
                        .bindWith(game)
                }
            }
            .bindWith(game)
    }

    private val backingTerminable = CompositeTerminable.create()

    override fun close()
    {
        backingTerminable.close()
    }

    override fun with(autoCloseable: AutoCloseable?) = backingTerminable.with(autoCloseable)

    override fun cleanup()
    {
        backingTerminable.cleanup()
    }

    inner class HungerGamesScoreboard(override val game: AbstractMiniGameGameImpl<HungerGamesGameConfiguration>) :
        MiniGameScoreboard
    {
        override fun titleFor(player: Player) = "${CC.BD_RED}SG"

        fun Player.addSurrounding(board: MutableList<String>, surrounded: () -> List<String>)
        {
            board.surround { extra ->
                extra += surrounded()
                extra += ""
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                    game.allNonSpectators().size
                }/${configuration.mode.maxPlayers()}"
                extra += ""
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Kills: ${CC.WHITE}${
                    playerResourcesOf(this).kills
                }"
                extra += ""
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Map: ${CC.WHITE}${game.map.displayName}"
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Mode: ${CC.WHITE}${configuration.mode.displayName}"
            }
        }

        override fun createWaitingScoreboardFor(player: Player, board: MutableList<String>)
        {
            board.surround { extra ->
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Players: ${CC.WHITE}${
                    game.allNonSpectators().size
                }/${configuration.mode.maxPlayers()}"
                extra += ""
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Waiting for players..."
                extra += ""
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Map: ${CC.WHITE}${game.map.displayName}"
                extra += "${CC.D_RED}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Mode: ${CC.WHITE}${configuration.mode.displayName}"
            }
        }

        override fun createStartingScoreboardFor(player: Player, board: MutableList<String>)
        {
            player.addSurrounding(board) {
                listOf(
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Next Event:",
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GREEN}Start ${TimeUtil.formatIntoMMSS(game.startCountDown)}"
                )
            }
        }

        override fun createInGameScoreboardFor(player: Player, board: MutableList<String>)
        {
            player.addSurrounding(board) {
                listOf(
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Next Event:",
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GREEN}${
                        game.tracker.nextEvent()?.description ?: "None!"
                    } in ${
                        TimeUtil.formatIntoMMSS(
                            game.tracker.timeUntilNextEvent().toInt() / 1000
                        )
                    }"
                )
            }
        }

        override fun createEndingScoreboardFor(player: Player, report: GameReport?, board: MutableList<String>)
        {
            player.addSurrounding(board) {
                listOf(
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GRAY}Next Event:",
                    "${CC.GREEN}${Constants.THIN_VERTICAL_LINE} ${CC.GREEN}Game Ended!"
                )
            }
        }
    }
}
