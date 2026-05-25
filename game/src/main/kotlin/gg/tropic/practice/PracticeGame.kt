package gg.tropic.practice

import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.annotations.container.ContainerEnable
import gg.scala.commons.core.plugin.*
import gg.scala.commons.preconfigure.PreConfigureSubTypeProcessor
import gg.scala.lemon.channel.ChatChannelService
import gg.scala.lemon.redirection.aggregate.ServerAggregateHandler
import gg.scala.lemon.redirection.aggregate.impl.LeastTrafficServerAggregateHandler
import gg.tropic.practice.games.GameService
import gg.tropic.practice.metadata.SystemMetadataService
import gg.tropic.practice.minigame.*
import gg.tropic.practice.settings.ChatVisibility
import gg.tropic.practice.settings.DuelsSettingCategory
import gg.tropic.practice.schematics.manipulation.BlockChanger
import gg.tropic.practice.ugc.WorldInstanceProviderType
import gg.tropic.practice.ugc.toHostedWorld
import org.bukkit.Bukkit

/**
 * @author GrowlyX
 * @since 8/4/2022
 */
@Plugin(
    name = "Minigames",
    version = "%remote%/%branch%/%id%",
    description = "Game"
)
@PluginAuthor("ArchMC")
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("CoreGameExtensions"),
    PluginDependency("ScBasics"),
    PluginDependency("Parties"),
    // Legacy
    PluginDependency("SlimeWorldManager", soft = true),
    // Modern
    PluginDependency("SlimeWorldPlugin", soft = true),
    PluginDependency("ASPaperPlugin", soft = true),
    PluginDependency("cloudsync", soft = true),
    PluginDependency("Friends", soft = true),
    PluginDependency("ScStaff", soft = true),

    // The goal of this dependency is to attempt to ensure that listeners here are registered after polar
    // so if polar cancels an event the listeners can actually see it is cancelled
    PluginDependency("PolarLoader", soft = true),
)
class PracticeGame : ExtendedScalaPlugin()
{
    init
    {
        PracticeShared
    }

    @ContainerEnable
    fun containerEnable()
    {
        devProvider = {
            "mipgamedev" in ServerSync.getLocalGameServer().groups
        }

        BlockChanger.load(this, false)

        MiniGameSerializers.configure()
        PreConfigureSubTypeProcessor.register<BasicMiniGameOrchestrator<*>> {
            MiniGameRegistry.miniGameOrchestrators[it.id] = it
        }

        ChatChannelService.default
            .displayToPlayer { player, viewer ->
                val chatVisibility = BasicsProfileService.find(viewer)
                    ?.setting(
                        "${DuelsSettingCategory.DUEL_SETTING_PREFIX}:chat-visibility",
                        ChatVisibility.Global
                    )
                    ?: ChatVisibility.Global

                val bukkitPlayer = Bukkit.getPlayer(player)
                val game = GameService.byPlayerOrSpectator(viewer.uniqueId)
                val viewerHostedWorld = viewer.toHostedWorld()
                val senderHostedWorld = bukkitPlayer?.toHostedWorld()

                // Realms are isolated from non-realm contexts (duels, duels
                // lobby, etc.). A realm viewer should only see chat from other
                // realm players (cross-realm OK), and realm chat shouldn't
                // leak into non-realm contexts either. For same-server senders
                // we check their local hosted world; for cross-server senders
                // we consult the network-wide hosted-world cache so we can
                // tell whether a remote sender is currently in a realm.
                val viewerInRealm = viewerHostedWorld?.providerType ==
                    WorldInstanceProviderType.REALM
                val senderInRealm = if (bukkitPlayer != null)
                {
                    senderHostedWorld?.providerType ==
                        WorldInstanceProviderType.REALM
                } else
                {
                    SystemMetadataService.allHostedWorldInstances().any {
                        it.type == WorldInstanceProviderType.REALM &&
                            player in it.onlinePlayers
                    }
                }
                if (viewerInRealm != senderInRealm)
                {
                    return@displayToPlayer false
                }
                if (viewerInRealm)
                {
                    // both sides are in realms — allow cross-realm chat
                    return@displayToPlayer true
                }

                if (viewerHostedWorld != null && viewerHostedWorld.providerType != WorldInstanceProviderType.REALM)
                {
                    return@displayToPlayer senderHostedWorld != null &&
                        senderHostedWorld.globalId == viewerHostedWorld.globalId
                }

                if (
                    bukkitPlayer != null &&
                    senderHostedWorld != null &&
                    senderHostedWorld.providerType != WorldInstanceProviderType.REALM ||
                    game is AbstractMiniGameGameImpl<*>
                )
                {
                    return@displayToPlayer bukkitPlayer != null && bukkitPlayer.world.name == viewer.world.name
                }

                when (chatVisibility)
                {
                    ChatVisibility.Global -> true
                    else -> bukkitPlayer != null && bukkitPlayer.world.name == viewer.world.name
                }
            }

        val lobbyRedirector = LeastTrafficServerAggregateHandler(
            lobbyGroup().suffixWhenDev()
        )
        lobbyRedirector.subscribe()

        flavor {
            bind<ServerAggregateHandler>() to lobbyRedirector
        }

        Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(),
            "spark profiler start --thread * --only-ticks-over 25"
        )
    }
}
