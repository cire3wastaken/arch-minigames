package mc.arch.minigames.persistent.housing.game.command

import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.acf.annotation.CommandAlias
import gg.scala.commons.acf.annotation.Default
import gg.scala.commons.acf.annotation.Description
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.resources.getPlayerHouseFromInstance
import mc.arch.minigames.persistent.housing.game.worldedit.WorldEditOperations
import mc.arch.minigames.persistent.housing.game.worldedit.WorldEditService
import net.evilblock.cubed.util.CC
import org.bukkit.entity.Player

/**
 * In-house WorldEdit commands. Each //xx alias is implemented as its own
 * object so the ACF Bukkit registration treats them as independent
 * commands - matches WorldEdit's UX (`//set`, `//cut`, etc.) without
 * forcing a parent command.
 */
object WorldEditCommand
{
    fun requireHouseAndPermission(sender: ScalaPlayer): Pair<Player, PlayerHouse>
    {
        val player = sender.bukkit()
        val house = player.getPlayerHouseFromInstance()
            ?: throw ConditionFailedException("You are not currently visiting a realm!")
        if (!WorldEditService.canUseWorldEdit(player, house))
        {
            throw ConditionFailedException("You don't have permission to use WorldEdit here.")
        }
        return player to house
    }

    fun report(player: Player, result: WorldEditOperations.WorldEditResult)
    {
        when (result)
        {
            is WorldEditOperations.WorldEditResult.Success ->
                player.sendMessage("${CC.GREEN}Operation complete. ${CC.GRAY}(${result.blocksAffected} blocks)")
            is WorldEditOperations.WorldEditResult.Failure ->
                player.sendMessage("${CC.RED}${result.reason}")
        }
    }
}

@AutoRegister
object WorldEditWandCommand : ScalaCommand()
{
    @CommandAlias("/wand")
    @Description("Receive the realm WorldEdit selection wand.")
    fun onWand(sender: ScalaPlayer)
    {
        val (player, _) = WorldEditCommand.requireHouseAndPermission(sender)
        player.inventory.addItem(WorldEditService.wandItem.clone())
        player.sendMessage(
            "${CC.GREEN}Given WorldEdit wand. ${CC.GRAY}Left-click = pos1, right-click = pos2."
        )
    }
}

@AutoRegister
object WorldEditSetCommand : ScalaCommand()
{
    @CommandAlias("/set")
    @Description("Fill the selection with a material.")
    fun onSet(sender: ScalaPlayer, material: String)
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        val xmat = WorldEditOperations.parseMaterial(material)
            ?: throw ConditionFailedException("Unknown material: $material")
        WorldEditCommand.report(player, WorldEditOperations.set(player, house, xmat))
    }
}

@AutoRegister
object WorldEditCutCommand : ScalaCommand()
{
    @CommandAlias("/cut")
    @Description("Copy the selection to the clipboard, then air out the source.")
    fun onCut(sender: ScalaPlayer)
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        WorldEditCommand.report(player, WorldEditOperations.cut(player, house))
    }
}

@AutoRegister
object WorldEditCopyCommand : ScalaCommand()
{
    @CommandAlias("/copy")
    @Description("Copy the selection to the clipboard, anchored at your block.")
    fun onCopy(sender: ScalaPlayer)
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        WorldEditCommand.report(player, WorldEditOperations.copy(player, house))
    }
}

@AutoRegister
object WorldEditPasteCommand : ScalaCommand()
{
    @CommandAlias("/paste")
    @Description("Paste the clipboard relative to your block.")
    fun onPaste(sender: ScalaPlayer)
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        WorldEditCommand.report(player, WorldEditOperations.paste(player, house))
    }
}

@AutoRegister
object WorldEditSphereCommand : ScalaCommand()
{
    @CommandAlias("/sphere")
    @Description("Generate a sphere of the given material and radius at your block.")
    fun onSphere(
        sender: ScalaPlayer,
        material: String,
        radius: Int,
        @Default("false") hollow: Boolean
    )
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        val xmat = WorldEditOperations.parseMaterial(material)
            ?: throw ConditionFailedException("Unknown material: $material")
        WorldEditCommand.report(
            player,
            WorldEditOperations.sphere(player, house, xmat, radius, hollow)
        )
    }
}

@AutoRegister
object WorldEditHsphereCommand : ScalaCommand()
{
    @CommandAlias("/hsphere")
    @Description("Generate a hollow sphere of the given material and radius at your block.")
    fun onHsphere(sender: ScalaPlayer, material: String, radius: Int)
    {
        val (player, house) = WorldEditCommand.requireHouseAndPermission(sender)
        val xmat = WorldEditOperations.parseMaterial(material)
            ?: throw ConditionFailedException("Unknown material: $material")
        WorldEditCommand.report(
            player,
            WorldEditOperations.sphere(player, house, xmat, radius, hollow = true)
        )
    }
}

@AutoRegister
object WorldEditPos1Command : ScalaCommand()
{
    @CommandAlias("/pos1")
    @Description("Set pos1 to your current block.")
    fun onPos1(sender: ScalaPlayer)
    {
        val (player, _) = WorldEditCommand.requireHouseAndPermission(sender)
        val loc = player.location
        WorldEditService.sessionOf(player).setPos1(loc.toVector(), loc.world.name)
        player.sendMessage(
            "${CC.GREEN}pos1 ${CC.GRAY}set to ${CC.WHITE}${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"
        )
    }
}

@AutoRegister
object WorldEditPos2Command : ScalaCommand()
{
    @CommandAlias("/pos2")
    @Description("Set pos2 to your current block.")
    fun onPos2(sender: ScalaPlayer)
    {
        val (player, _) = WorldEditCommand.requireHouseAndPermission(sender)
        val loc = player.location
        WorldEditService.sessionOf(player).setPos2(loc.toVector(), loc.world.name)
        player.sendMessage(
            "${CC.GREEN}pos2 ${CC.GRAY}set to ${CC.WHITE}${loc.blockX}, ${loc.blockY}, ${loc.blockZ}"
        )
    }
}
