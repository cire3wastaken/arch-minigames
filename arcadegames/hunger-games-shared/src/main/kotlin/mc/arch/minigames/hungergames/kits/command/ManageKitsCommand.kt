package mc.arch.minigames.hungergames.kits.command

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.commons.acf.annotation.Single
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import mc.arch.minigames.hungergames.kits.HungerGamesKit
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import mc.arch.minigames.hungergames.kits.menu.ListManageableKitsMenu
import net.evilblock.cubed.util.CC

/**
 * @author ArchMC
 */
@AutoRegister
object ManageKitsCommand : ScalaCommand()
{
    @CommandAlias("managehgkits")
    @CommandPermission("op")
    fun onManageKits(player: ScalaPlayer) = ListManageableKitsMenu().openMenu(player)

    @CommandAlias("createhgkit")
    @CommandPermission("op")
    fun onCreateKit(player: ScalaPlayer, @Single id: String)
    {
        val existing = HungerGamesKitDataSync.cached().kits[id]
        if (existing != null)
        {
            player.sendMessage("${CC.RED}A kit with ID '$id' already exists!")
            return
        }

        HungerGamesKitDataSync.editAndSave {
            kits[id] = HungerGamesKit(
                id = id,
                displayName = id.replaceFirstChar { it.uppercase() },
                icon = player.bukkit().itemInHand.clone()
            )
        }

        player.sendMessage("${CC.GREEN}Kit '$id' created! Use /managehgkits to edit.")
    }
}
