package mc.arch.minigames.persistent.housing.game.menu.player

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.game.inventory.HousingInventoryService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player

class PlayerInteractViewMenu(val house: PlayerHouse, val other: Player): Menu("Viewing Info: ${other.name}")
{
    init
    {
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>): Int = 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()
        val admin = house.playerIsOrAboveAdministrator(player.uniqueId)

        if (admin)
        {
            buttons[11] = ItemBuilder.of(XMaterial.BARRIER)
                .name("${CC.GREEN}Ban User")
                .addToLore(
                    "${CC.GRAY}Ban this user indefinitely from",
                    "${CC.GRAY}your house!",
                    "",
                    "${CC.YELLOW}Click to deliver the banhammer!"
                ).toButton { _, _ ->
                    if (!house.housingBans.contains(other.uniqueId))
                    {
                        house.housingBans.add(other.uniqueId)
                        house.save().join()
                    }

                    if (other.isOnline)
                    {
                        other.kickPlayer("${CC.RED}You have been banned from this house!")
                    }

                    player.sendMessage("${CC.WHITE}${other.displayName} ${CC.GREEN}has been permanently banned by ${CC.WHITE}${player.displayName}")
                    player.closeInventory()
                }

            buttons[13] = ItemBuilder.of(XMaterial.CAULDRON)
                .name("${CC.GREEN}Clear Inventory")
                .addToLore(
                    "${CC.GRAY}Wipe every item from this player's",
                    "${CC.GRAY}inventory, including armor.",
                    "",
                    "${CC.GRAY}The realm info nether star will be",
                    "${CC.GRAY}preserved.",
                    "",
                    "${CC.B_RED}WARNING ${CC.RED}This cannot be undone!",
                    "",
                    "${CC.YELLOW}Click to clear their inventory!"
                ).toButton { _, _ ->
                    if (!other.isOnline)
                    {
                        player.sendMessage("${CC.RED}${other.name} is no longer online.")
                        player.closeInventory()
                        return@toButton
                    }

                    HousingInventoryService.clearInventory(other)
                    other.sendMessage("${CC.RED}Your inventory has been cleared by a realm administrator.")
                    player.sendMessage("${CC.B_GREEN}SUCCESS! ${CC.GREEN}Cleared ${CC.WHITE}${other.name}${CC.GREEN}'s inventory.")
                    player.closeInventory()
                }

            buttons[15] = ItemBuilder.of(XMaterial.BARRIER)
                .name("${CC.GREEN}Kick User")
                .addToLore(
                    "${CC.GRAY}Kick this user temporarily from",
                    "${CC.GRAY}your house!",
                    "",
                    "${CC.RED}Note: This player can join back after",
                    "",
                    "${CC.YELLOW}Click to deliver the banhammer!"
                ).toButton { _, _ ->
                    if (other.isOnline)
                    {
                        other.kickPlayer("${CC.RED}You have been kicked from this house!")
                    }

                    player.sendMessage("${CC.WHITE}${other.displayName} ${CC.GREEN}has been kicked by ${CC.WHITE}${player.displayName}")
                    player.closeInventory()
                }
        }

        return buttons
    }
}
