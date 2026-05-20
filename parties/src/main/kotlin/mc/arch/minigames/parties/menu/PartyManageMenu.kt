package mc.arch.minigames.parties.menu

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.acf.ConditionFailedException
import mc.arch.minigames.parties.command.PartyCommand
import mc.arch.minigames.parties.model.Party
import mc.arch.minigames.parties.model.PartyRole
import mc.arch.minigames.parties.model.PartySetting
import mc.arch.minigames.parties.model.PartyStatus
import mc.arch.minigames.parties.service.NetworkPartyService
import mc.arch.minigames.parties.stream.PartyMessageStream
import mc.arch.minigames.parties.toDisplayName
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.menu.pagination.PaginatedMenu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.*
import net.evilblock.cubed.util.bukkit.prompt.InputPrompt
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import java.util.concurrent.CompletableFuture

/**
 * @author GrowlyX
 * @since 2/25/2022
 */
class PartyManageMenu(
    private val party: Party,
    private val role: PartyRole
) : Menu("Party ${Constants.DOUBLE_ARROW_RIGHT} Management")
{
    init
    {
        updateAfterClick = true
    }

    override fun size(buttons: Map<Int, Button>) = 27
    override fun getButtons(player: Player): Map<Int, Button>
    {
        return mutableMapOf<Int, Button>().apply {
            this[10] = MultiOptionPlayerSettingsBuilder()
                .titleOf("${CC.GREEN}Visibility")
                .materialOf(XMaterial.ENDER_EYE)
                .descriptionOf(
                    "${CC.GRAY}What visibility setting",
                    "${CC.GRAY}would you like this party",
                    "${CC.GRAY}to use?"
                )
                .orderedValuesOf(
                    "Public",
                    "Protected",
                    "Private"
                )
                .fallbackOf("Private")
                .providerOverrideOf { _, _ ->
                    party.status.capitalized
                }
                .valueOverrideOf {
                    val status = PartyStatus
                        .valueOf(it.uppercase())

                    party.status = status
                    party.saveAndUpdateParty().thenRun {
                        PartyMessageStream.pushToStream(
                            party, FancyMessage()
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()} ${CC.YELLOW}updated party visibility to ${CC.AQUA}${party.status.formatted}${CC.YELLOW}!\n")
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
                        )
                    }
                }
                .asButton()

            this[11] = MultiOptionPlayerSettingsBuilder()
                .titleOf("${CC.GREEN}All Invite")
                .materialOf(XMaterial.FIRE_CHARGE)
                .descriptionOf(
                    "${CC.GRAY}Do you want all party",
                    "${CC.GRAY}members to be able to",
                    "${CC.GRAY}invite players?"
                )
                .orderedValuesOf(
                    "Enabled",
                    "Disabled"
                )
                .fallbackOf("Enabled")
                .providerOverrideOf { _, _ ->
                    if (party.isEnabled(PartySetting.ALL_INVITE))
                        "Enabled"
                    else
                        "Disabled"
                }
                .valueOverrideOf {
                    party.update(PartySetting.ALL_INVITE, it == "Enabled")
                    party.saveAndUpdateParty().thenRun {
                        PartyMessageStream.pushToStream(
                            party, FancyMessage()
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()} ${CC.YELLOW}$it ${CC.AQUA}All-Invite${CC.YELLOW}!\n")
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
                        )
                    }
                }
                .asButton()

            this[12] = MultiOptionPlayerSettingsBuilder()
                .titleOf("${CC.GREEN}Chat Muted")
                .materialOf(XMaterial.BLAZE_POWDER)
                .descriptionOf(
                    "${CC.GRAY}Do you want chat to",
                    "${CC.GRAY}be muted?",
                )
                .orderedValuesOf(
                    "Enabled",
                    "Disabled"
                )
                .fallbackOf("Disabled")
                .providerOverrideOf { _, _ ->
                    if (party.isEnabled(PartySetting.CHAT_MUTED))
                        "Enabled"
                    else
                        "Disabled"
                }
                .valueOverrideOf {
                    party.update(PartySetting.CHAT_MUTED, it == "Enabled")
                    party.saveAndUpdateParty().thenRun {
                        PartyMessageStream.pushToStream(
                            party, FancyMessage()
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()} ${CC.YELLOW}$it ${CC.RED}Chat Muted${CC.YELLOW}!\n")
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
                        )
                    }
                }
                .asButton()

            this[13] = ItemBuilder(XMaterial.IRON_DOOR)
                .name("${CC.GREEN}Party Limit")
                .addToLore(
                    "${CC.GRAY}Set the party player limit.",
                    "",
                    "${CC.WHITE}Current: ${CC.GOLD}${party.limit}",
                    "",
                    "${CC.YELLOW}Right-Click to decrease the limit by 1",
                    "${CC.YELLOW}Left-Click to increase the limit by 1",
                )
                .toButton { _, type ->
                    when (type) {
                        ClickType.RIGHT ->
                        {
                            if (party.limit - 1 < 2)
                            {
                                player.sendMessage("${CC.RED}You cannot go out of the party member limit bounds!")
                                return@toButton
                            }

                            party.limit--
                        }
                        ClickType.LEFT ->
                        {
                            if (party.limit + 1 > 100)
                            {
                                player.sendMessage("${CC.RED}You cannot go out of the party member limit bounds!")
                                return@toButton
                            }

                            party.limit++
                        }
                        else -> {}
                    }

                    party.saveAndUpdateParty()
                }

            this[14] = MultiOptionPlayerSettingsBuilder()
                .titleOf("${CC.GREEN}Party Auto Warp")
                .materialOf(XMaterial.COMPASS)
                .descriptionOf(
                    "${CC.GRAY}Do you want to automatically",
                    "${CC.GRAY}warp all party members when",
                    "${CC.GRAY}you change servers?"
                )
                .orderedValuesOf(
                    "Enabled",
                    "Disabled"
                )
                .fallbackOf("Disabled")
                .providerOverrideOf { _, _ ->
                    if (party.isEnabled(PartySetting.AUTO_WARP))
                        "Enabled"
                    else
                        "Disabled"
                }
                .valueOverrideOf {
                    party.update(PartySetting.AUTO_WARP, it == "Enabled")
                    party.saveAndUpdateParty().thenRun {
                        PartyMessageStream.pushToStream(
                            party, FancyMessage()
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()} ${CC.YELLOW}$it ${CC.GREEN}Auto-Warp${CC.YELLOW}!\n")
                                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
                        )
                    }
                }
                .asButton()


            this[1] = ItemBuilder(XMaterial.OAK_SIGN)
                .name("${CC.GREEN}Party Password")
                .addToLore(
                    "${CC.GRAY}Update your party password",
                    "${CC.GRAY}which is effective during",
                    "${CC.GRAY}party protected mode.",
                    ""
                )
                .apply {
                    if (party.status == PartyStatus.PROTECTED)
                    {
                        addToLore("${CC.YELLOW}Right-Click to update password.")
                        addToLore("${CC.YELLOW}Left-Click to view password.")
                    } else
                    {
                        addToLore("${CC.RED}The party must be in protected mode!")
                    }
                }
                .toButton { _, type ->
                    if (party.status != PartyStatus.PROTECTED)
                    {
                        player.sendMessage("${CC.RED}Your party is not in protected mode!")
                        return@toButton
                    }

                    if (type!!.isRightClick)
                    {
                        player.closeInventory()

                        InputPrompt().apply {
                            this.withText("${CC.GREEN}Please enter a new server password!")
                            this.acceptInput { _, password ->
                                val fancyMessage = FancyMessage()
                                party.password = password

                                fancyMessage.withMessage(
                                    "${CC.SEC}The password is now: ${CC.WHITE}${
                                        "*".repeat(party.password.length)
                                    } ${CC.I_GRAY}(hover over to view)"
                                )

                                fancyMessage.andHoverOf(party.password)

                                party.saveAndUpdateParty().thenRun {
                                    fancyMessage.sendToPlayer(player)
                                }
                            }

                            this.start(player)
                        }
                    } else
                    {
                        if (party.password.isEmpty())
                        {
                            player.sendMessage("${CC.RED}Your party does not have a password!")
                            return@toButton
                        }

                        val fancyMessage = FancyMessage()
                        fancyMessage.withMessage(
                            "${CC.SEC}The password is: ${CC.WHITE}${
                                "*".repeat(party.password.length)
                            } ${CC.I_GRAY}(hover over to view)"
                        )

                        fancyMessage.andHoverOf(party.password)
                        fancyMessage.sendToPlayer(player)
                    }
                }

            this[2] = ItemBuilder(XMaterial.COMPARATOR)
                .name("${CC.RED}Reset Password")
                .addToLore(
                    "${CC.GRAY}Set your party password",
                    "${CC.GRAY}back to its default.",
                    "",
                    "${CC.YELLOW}Click to reset password."
                )
                .toButton { _, _ ->
                    if (party.password.isEmpty())
                    {
                        player.sendMessage("${CC.RED}Your party does not have a password!")
                        return@toButton
                    }

                    party.password = ""
                    party.saveAndUpdateParty().thenRun {
                        player.sendMessage("${CC.GREEN}Your party's password has been reset.")
                    }
                }

            this[9] = ItemBuilder
                .copyOf(
                    PaginatedMenu.PLACEHOLDER
                        .getButtonItem(player)
                )
                .data(5)
                .name("${CC.GREEN}Settings")
                .toButton()

            this[0] = ItemBuilder
                .copyOf(
                    PaginatedMenu.PLACEHOLDER
                        .getButtonItem(player)
                )
                .data(3)
                .name("${CC.D_AQUA}Password")
                .toButton()

            if (role == PartyRole.LEADER)
            {
                listOf(7, 16, 25).forEach {
                    this[it] = PaginatedMenu.PLACEHOLDER
                }

                this[8 + 9] = ItemBuilder(XMaterial.BEACON)
                    .name("${CC.GREEN}Warp your party")
                    .addToLore(
                        "${CC.GRAY}Send all your party members",
                        "${CC.GRAY}to your current server!",
                        "",
                        "${CC.YELLOW}Click to warp members!"
                    )
                    .toButton { _, _ ->
                        CompletableFuture.runAsync {
                            NetworkPartyService.warpPartyHere(party)
                        }

                        player.closeInventory()
                        player.sendMessage("${CC.GREEN}You've warped party members to your server!")
                    }

                val privateGamesEnabled = party.isEnabled(PartySetting.PRIVATE_GAMES)
                this[8] = ItemBuilder
                    .of(if (privateGamesEnabled) XMaterial.PINK_DYE else XMaterial.GRAY_DYE)
                    .name("${CC.LIGHT_PURPLE}Private Games")
                    .addToLore(
                        "${CC.GRAY}Create private game sessions",
                        "${CC.GRAY}for your party with custom",
                        "${CC.GRAY}settings and no stat tracking!",
                        "",
                        "${CC.WHITE}Status: ${if (privateGamesEnabled) "${CC.GREEN}Enabled" else "${CC.RED}Disabled"}",
                        "",
                        "${CC.YELLOW}Click to toggle!"
                    )
                    .toButton { _, _ ->
                        val wasEnabled = party.isEnabled(PartySetting.PRIVATE_GAMES)
                        party.update(PartySetting.PRIVATE_GAMES, !wasEnabled)
                        party.saveAndUpdateParty().thenRun {
                            PartyManageMenu(party, role).openMenu(player)
                        }
                    }

                this[8 + 18] = ItemBuilder(XMaterial.RED_DYE)
                    .name("${CC.GREEN}Disband Party")
                    .addToLore(
                        "${CC.GRAY}Disband your party and",
                        "${CC.GRAY}notify party members.",
                        "",
                        "${CC.YELLOW}Click to disband party."
                    )
                    .toButton { _, _ ->
                        try
                        {
                            PartyCommand.onDisband(player)
                        } catch (exception: ConditionFailedException)
                        {
                            player.sendMessage("${CC.RED}${exception.message}")
                        }

                        // as this is an updating menu
                        Tasks.delayed(2L) {
                            player.closeInventory()
                        }
                    }
            }
        }
    }
}
