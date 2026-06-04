package gg.tropic.practice.privategames

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.tropic.practice.expectation.ExpectationService
import gg.tropic.practice.games.GameState
import gg.tropic.practice.games.event.PlayerJoinGameEvent
import gg.tropic.practice.privategames.menu.PrivateGameSettingsMenu
import gg.tropic.practice.privategames.settings.PrivateGameSettingsRegistry
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that adds hotbar items for private games in the waiting lobby.
 * Allows the party leader to configure game settings before the game starts.
 *
 * @author GrowlyX
 * @since 12/22/24
 */
@Service
object PrivateGamesHotbarService
{
    @Inject
    lateinit var plugin: ExtendedScalaPlugin

    private val settingsItem = ItemBuilder
        .of(XMaterial.COMPARATOR)
        .name("${CC.LIGHT_PURPLE}Game Settings ${CC.GRAY}(Right Click)")
        .addToLore(
            "${CC.GRAY}Configure private game",
            "${CC.GRAY}settings before start!"
        )
        .build()

    private const val SETTINGS_SLOT = 2

    private val rateLimits = mutableMapOf<UUID, Long>()
    private val refreshing = ConcurrentHashMap.newKeySet<UUID>()
    private val greeted = ConcurrentHashMap.newKeySet<UUID>()

    @Configure
    fun configure()
    {
        Events
            .subscribe(PlayerJoinGameEvent::class.java)
            .filter { it.game.expectationModel.isPrivateGame }
            .filter { it.game.state == GameState.Waiting || it.game.state == GameState.Starting }
            .handler { event ->
                val game = event.game
                if (!refreshing.add(game.identifier))
                {
                    return@handler
                }

                Schedulers
                    .sync()
                    .runRepeating({ task ->
                        if (!(game.state(GameState.Waiting) || game.state(GameState.Starting)))
                        {
                            task.closeAndReportException()
                            refreshing.remove(game.identifier)
                            greeted.remove(game.identifier)
                            return@runRepeating
                        }

                        val leaderId = game.expectationModel.players.firstOrNull()
                            ?: return@runRepeating
                        val leader = Bukkit.getPlayer(leaderId)
                            ?: return@runRepeating

                        var changed = false
                        if (!settingsItem.isSimilar(leader.inventory.getItem(SETTINGS_SLOT)))
                        {
                            leader.inventory.setItem(SETTINGS_SLOT, settingsItem)
                            changed = true
                        }

                        if (changed)
                        {
                            leader.updateInventory()
                            if (greeted.add(game.identifier))
                            {
                                leader.sendMessage("${CC.PINK}This is a Private Game! ${CC.GRAY}Right-click the comparator to configure settings and force start.")
                            }
                        }
                    }, 0L, 20L)
                    .bindWith(game)
            }
            .bindWith(plugin)

        // Handle settings item click
        Events
            .subscribe(PlayerInteractEvent::class.java)
            .filter {
                it.hasItem() &&
                it.item.isSimilar(settingsItem) &&
                (it.action == Action.RIGHT_CLICK_AIR || it.action == Action.RIGHT_CLICK_BLOCK)
            }
            .handler { event ->
                val game = gg.tropic.practice.games.GameService.byPlayer(event.player)
                    ?: return@handler

                if (!game.expectationModel.isPrivateGame || !(game.state(GameState.Waiting) || game.state(GameState.Starting)))
                {
                    return@handler
                }

                // Rate limit check
                val lastClick = rateLimits[event.player.uniqueId] ?: 0L
                if (System.currentTimeMillis() - lastClick < 250L)
                {
                    return@handler
                }
                rateLimits[event.player.uniqueId] = System.currentTimeMillis()

                // Check if player is party leader (first in players list)
                if (game.expectationModel.players.firstOrNull() != event.player.uniqueId)
                {
                    event.player.sendMessage("${CC.RED}Only the party leader can modify game settings!")
                    return@handler
                }

                if (!(game.state == GameState.Waiting || game.state == GameState.Starting))
                {
                    event.player.sendMessage("${CC.RED}You can only modify settings before the game starts!")
                    return@handler
                }

                // Get game type from minigame lifecycle
                val typeId = game.miniGameLifecycle?.let {
                    game.flagMetaData(gg.tropic.practice.kit.feature.FeatureFlag.MiniGameType, "id")
                } ?: "default"

               val kitId = game.expectationModel.kitId
                val gameType = if (PrivateGameSettingsRegistry.getSettingsFor(kitId).isNotEmpty())
                {
                    kitId
                } else
                {
                    typeId
                }

                PrivateGameSettingsMenu(game, gameType).openMenu(event.player)
            }
            .bindWith(plugin)
    }
}
