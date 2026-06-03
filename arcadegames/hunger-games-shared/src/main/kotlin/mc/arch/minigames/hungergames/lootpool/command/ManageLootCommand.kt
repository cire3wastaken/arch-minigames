package mc.arch.minigames.hungergames.lootpool.command

import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.CommandPermission
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import mc.arch.minigames.hungergames.lootpool.menu.ViewLootTypesMenu

/**
 * @author ArchMC
 */
@AutoRegister
object ManageLootCommand : ScalaCommand()
{
    @CommandAlias("managehgloot")
    @CommandPermission("op")
    fun onManageLootPool(player: ScalaPlayer) = ViewLootTypesMenu().openMenu(player)
}
