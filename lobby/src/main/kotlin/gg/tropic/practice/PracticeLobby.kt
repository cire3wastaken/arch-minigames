package gg.tropic.practice

import gg.scala.basics.plugin.settings.SettingMenu
import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.annotations.ServiceablePackage
import gg.scala.commons.annotations.container.ContainerEnable
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.core.plugin.*
import gg.scala.lemon.channel.ChatChannelService
import gg.tropic.practice.metadata.SystemMetadataService
import gg.tropic.practice.minigame.MiniGameSerializers
import gg.tropic.practice.ugc.WorldInstanceProviderType

/**
 * @author GrowlyX
 * @since 8/5/2022
 */
@Plugin(
    name = "Minigames",
    version = "%remote%/%branch%/%id%",
    description = "Lobby"
)
@PluginAuthor("ArchMC")
@PluginWebsite("https://arch.mc")
@PluginDependencyComposite(
    PluginDependency("scala-commons"),
    PluginDependency("Lemon"),
    PluginDependency("ScBasics"),
    PluginDependency("Parties"),
    PluginDependency("CoreGameExtensions"),
    PluginDependency("ScStaff", soft = true),
    PluginDependency("ScQueue", soft = true),
    PluginDependency("PlaceholderAPI", soft = true),
    PluginDependency("SkinsRestorer", soft = true),
    PluginDependency("Friends", soft = true),
)
@ServiceablePackage("mc.arch.minigames.microgames.bridging")
class PracticeLobby : ExtendedScalaPlugin()
{
    init
    {
        PracticeShared
    }

    @ContainerEnable
    fun containerEnable()
    {
        devProvider = {
            "miplobbydev" in ServerSync.getLocalGameServer().groups
        }

        MiniGameSerializers.configure()
        SettingMenu.defaultCategory = "Minigames"

        // Realm chat is isolated from non-realm contexts. Lobby players
        // are never inside a realm hosted world themselves (realms live on
        // housing game servers), so we just hide chat from any sender who
        // is currently inside a realm. Same-server senders on a lobby are
        // never in a realm either, so the network-wide hosted-world cache
        // is the only check we need.
        ChatChannelService.default.displayToPlayer { player, _ ->
            val senderInRealm = SystemMetadataService
                .allHostedWorldInstances()
                .any {
                    it.type == WorldInstanceProviderType.REALM &&
                        player in it.onlinePlayers
                }
            !senderInRealm
        }
    }

    fun unregisterCommands(
        vararg commands: ScalaCommand
    )
    {
        commands.forEach {
            commandManager.unregisterCommand(it)
        }
    }
}
