package mc.arch.minigames.persistent.housing.game.menu.house

import com.cryptomorin.xseries.XMaterial
import gg.scala.lemon.filter.ChatMessageFilterHandler
import gg.scala.lemon.util.CallbackInputPrompt
import mc.arch.minigames.persistent.housing.api.content.HousingTime
import mc.arch.minigames.persistent.housing.api.content.HousingWeather
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.api.service.PlayerHousingService
import mc.arch.minigames.persistent.housing.game.menu.house.biome.HouseBiomeSelectionMenu
import mc.arch.minigames.persistent.housing.game.menu.house.events.EventActionSelectionMenu
import mc.arch.minigames.persistent.housing.game.menu.house.hologram.HologramEditorMenu
import mc.arch.minigames.persistent.housing.game.menu.house.npc.NPCEditorMenu
import mc.arch.minigames.persistent.housing.game.menu.house.player.PlayerManagementMenu
import mc.arch.minigames.persistent.housing.game.menu.house.roles.RoleEditorMenu
import mc.arch.minigames.persistent.housing.game.menu.house.settings.HouseSettingsMenu
import mc.arch.minigames.persistent.housing.game.menu.house.visitation.HouseVisitationRuleMenu
import mc.arch.minigames.persistent.housing.game.menu.house.music.HouseMusicSelectionMenu
import mc.arch.minigames.persistent.housing.game.menu.house.tags.HouseTagsEditorMenu
import mc.arch.minigames.persistent.housing.game.spatial.toWorldPosition
import mc.arch.minigames.persistent.housing.game.translateCC
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.GameMode
import org.bukkit.entity.Player

class MainHouseMenu(val house: PlayerHouse, val adminMenu: Boolean) : Menu("Viewing ${house.displayName}")
{
    init
    {
        if (!adminMenu)
        {
            placeholder = true
        }

        updateAfterClick = true
    }

    companion object
    {
        fun mainMenuButton(house: PlayerHouse) =
            ItemBuilder.of(XMaterial.NETHER_STAR)
                .name("${CC.GREEN}Main Menu")
                .toButton { player, _ ->
                    Button.playNeutral(player!!)
                    MainHouseMenu(house, true).openMenu(player)
                }
    }

    override fun size(buttons: Map<Int, Button>): Int = if (adminMenu) 45 else 27

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        if (adminMenu)
        {
            buttons[2] = ItemBuilder.of(XMaterial.REPEATER)
                .name("${CC.GREEN}Event Actions")
                .addToLore(
                    "${CC.GRAY}Want to trigger an action",
                    "${CC.GRAY}when someone joins or breaks a block?",
                    "${CC.GRAY}Check out our custom event action system.",
                    "",
                    "${CC.YELLOW}Click to view event actions!"
                ).toButton { _, _ ->
                    EventActionSelectionMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[3] = ItemBuilder.of(XMaterial.VILLAGER_SPAWN_EGG)
                .name("${CC.GREEN}NPC Editor")
                .addToLore(
                    "${CC.GRAY}Want to add custom NPCs to",
                    "${CC.GRAY}your realm? Edit them here!",
                    "",
                    "${CC.YELLOW}Click to view NPCs!"
                ).toButton { _, _ ->
                    NPCEditorMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[4] = ItemBuilder.of(XMaterial.OAK_SIGN)
                .name("${CC.GREEN}Hologram Editor")
                .addToLore(
                    "${CC.GRAY}Want to add custom Holograms to",
                    "${CC.GRAY}your realm? Edit them here!",
                    "",
                    "${CC.YELLOW}Click to view Holograms!"
                ).toButton { _, _ ->
                    HologramEditorMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[5] = ItemBuilder.of(XMaterial.FILLED_MAP)
                .name("${CC.GREEN}Groups and Permissions")
                .addToLore(
                    "${CC.GRAY}Want to add custom groups",
                    "${CC.GRAY}to your realm? Edit them here!",
                    "",
                    "${CC.YELLOW}Click to view groups!"
                ).toButton { _, _ ->
                    RoleEditorMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[6] = ItemBuilder.of(XMaterial.COMMAND_BLOCK)
                .name("${CC.GREEN}Player Management")
                .addToLore(
                    "${CC.GRAY}Want to ban players or give",
                    "${CC.GRAY}players roles? Do it here!",
                    "",
                    "${CC.YELLOW}Click to view player management panel!"
                ).toButton { _, _ ->
                    PlayerManagementMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[8] = ItemBuilder.of(XMaterial.CAULDRON)
                .name("${CC.GREEN}Clear Inventory")
                .addToLore(
                    "${CC.GRAY}Got a lot of items? Get rid of",
                    "${CC.GRAY}all of them here! You will not get",
                    "${CC.GRAY}them back, so be careful.",
                    "",
                    "${CC.YELLOW}Click to clear inventory!"
                ).toButton { _, _ ->

                }

            buttons[15] = ItemBuilder.of(XMaterial.GRASS_BLOCK)
                .name("${CC.GREEN}Biome")
                .addToLore(
                    "${CC.GRAY}Change the biome of",
                    "${CC.GRAY}your realm. Affects grass,",
                    "${CC.GRAY}water, and foliage tint.",
                    "",
                    "${CC.WHITE}Currently ${house.housingBiome?.displayName ?: "${CC.GRAY}Default"}",
                    "",
                    "${CC.YELLOW}Click to choose a biome!"
                ).toButton { _, _ ->
                    Button.playNeutral(player)
                    HouseBiomeSelectionMenu(house).openMenu(player)
                }

            buttons[11] = ItemBuilder.of(XMaterial.DEAD_BUSH)
                .name("${CC.GREEN}Weather")
                .addToLore(
                    "${CC.GRAY}Update the weather of",
                    "${CC.GRAY}your realm!",
                    "",
                    "${CC.WHITE}Currently ${(house.housingWeather ?: HousingWeather.CLEAR).displayName}",
                    "",
                    "${CC.YELLOW}Click to edit cycle options!"
                ).toButton { _, _ ->
                    val currentIndex = HousingWeather.entries.indexOf(house.housingWeather ?: HousingWeather.CLEAR)
                    val next = HousingWeather.entries.getOrElse(currentIndex + 1) { HousingWeather.CLEAR }

                    house.housingWeather = next
                    house.save()

                    Button.playNeutral(player)
                    player.sendMessage("${CC.YELLOW}Your weather has been updated to: ${next.displayName}")
                }

            buttons[12] = ItemBuilder.of(XMaterial.CLOCK)
                .name("${CC.GREEN}Time")
                .addToLore(
                    "${CC.GRAY}Update the time of",
                    "${CC.GRAY}your realm!",
                    "",
                    "${CC.WHITE}Currently ${(house.housingTime ?: HousingTime.NOON).displayName}",
                    "",
                    "${CC.YELLOW}Click to edit cycle options!"
                ).toButton { _, _ ->
                    val currentIndex = HousingTime.entries.indexOf(house.housingTime ?: HousingTime.NOON)
                    val next = HousingTime.entries.getOrElse(currentIndex + 1) { HousingTime.NOON }

                    house.housingTime = next
                    house.save()

                    Button.playNeutral(player)
                    player.sendMessage("${CC.YELLOW}Your time has been updated to: ${next.displayName}")
                }

            buttons[13] = ItemBuilder.of(XMaterial.CAULDRON)
                .name("${CC.GREEN}Allow Building Outside Zone")
                .addToLore(
                    "${CC.GRAY}Makes it so that people with",
                    "${CC.GRAY}elevated permissions can modify",
                    "${CC.GRAY}blocks outside of the grass zone.",
                    "",
                    "${CC.WHITE}Currently ${if (house.allowsMutatingOutsideRegion == true) "${CC.GREEN}Allowed" else "${CC.RED}Disallowed"}",
                    "",
                    "${CC.YELLOW}Click to edit cycle options!"
                ).toButton { _, _ ->

                    house.allowsMutatingOutsideRegion = !(house.allowsMutatingOutsideRegion ?: false)
                    house.save()

                    Button.playNeutral(player)
                    player.sendMessage("${CC.YELLOW}Your building zone status has been updated to: ${if (house.allowsMutatingOutsideRegion == true) "${CC.GREEN}Allowed" else "${CC.RED}Disallowed"}")
                }

            if (house.owner == player.uniqueId)
            {
                val gamemodeOrder = listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE)
                val gamemodeIcon = when (player.gameMode)
                {
                    GameMode.CREATIVE -> XMaterial.DIAMOND_PICKAXE
                    GameMode.ADVENTURE -> XMaterial.MAP
                    else -> XMaterial.GRASS_BLOCK
                }

                buttons[20] = ItemBuilder.of(gamemodeIcon)
                    .name("${CC.GREEN}Personal Gamemode")
                    .addToLore(
                        "${CC.GRAY}Update your personal gamemode",
                        "${CC.GRAY}while in your realm.",
                        "",
                        "${CC.WHITE}Currently ${player.gameMode.prettyName()}",
                        "",
                        "${CC.YELLOW}Click to cycle gamemode!"
                    ).toButton { _, _ ->
                        val currentIndex = gamemodeOrder.indexOf(player.gameMode)
                        val next = gamemodeOrder.getOrElse(currentIndex + 1) { gamemodeOrder.first() }

                        player.gameMode = next

                        Button.playNeutral(player)
                        player.sendMessage("${CC.YELLOW}Your gamemode has been updated to: ${next.prettyName()}")
                    }
            }

            buttons[14] = ItemBuilder.of(XMaterial.BEACON)
                .name("${CC.GREEN}Spawn Point")
                .addToLore(
                    "${CC.GRAY}Makes it so that on join,",
                    "${CC.GRAY}people will spawn at your current",
                    "${CC.GRAY}location.",
                    "",
                    "${CC.WHITE}Currently ${CC.GREEN}${house.spawnPoint?.x ?: 0}, ${house.spawnPoint?.y ?: 100}, ${house.spawnPoint?.z ?: 0}",
                    "",
                    "${CC.YELLOW}Click to set spawn location!"
                ).toButton { _, _ ->

                    house.spawnPoint = player.location.toWorldPosition()
                    house.save()

                    Button.playNeutral(player)
                    player.sendMessage("${CC.B_GREEN}SUCCESS! ${CC.GREEN}You have updated your realm's spawn point to your current location.")
                }

            buttons[27] = ItemBuilder.of(XMaterial.SPRUCE_DOOR)
                .name("${CC.GREEN}Travel to someone else's realm")
                .addToLore(
                    "${CC.GRAY}Want to see what others are up to?",
                    "${CC.GRAY}Come explore all open realms.",
                    "",
                    "${CC.YELLOW}Click to view realms you can join!"
                ).toButton { _, _ ->

                }

            buttons[36] = ItemBuilder.of(XMaterial.COMPASS)
                .name("${CC.GREEN}Search For Realm")
                .addToLore(
                    "${CC.GRAY}Want to search for a specific",
                    "${CC.GRAY}realm? Use this!",
                    "",
                    "${CC.YELLOW}Click to search for a realm!"
                ).toButton { _, _ ->

                }


            buttons[38] = ItemBuilder.of(XMaterial.PLAYER_HEAD)
                .name("${CC.GREEN}Visiting Rules")
                .addToLore(
                    "${CC.GRAY}Allows you to select who can",
                    "${CC.GRAY}and cannot visit your realm.",
                    "",
                    "${CC.YELLOW}Click to configure!"
                ).toButton { _, _ ->
                    HouseVisitationRuleMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[39] = ItemBuilder.of(XMaterial.COMPARATOR)
                .name("${CC.GREEN}Realm Settings")
                .addToLore(
                    "${CC.GRAY}Allows you to change and",
                    "${CC.GRAY}view specific settings about",
                    "${CC.GRAY}your realm.",
                    "",
                    "${CC.YELLOW}Click to configure!"
                ).toButton { _, _ ->
                    HouseSettingsMenu(house).openMenu(player)
                    Button.playNeutral(player)
                }

            buttons[40] = ItemBuilder.of(XMaterial.OAK_SIGN)
                .name("${CC.GREEN}Realm Name")
                .addToLore(
                    "${CC.GRAY}Allows you to change the",
                    "${CC.GRAY}name of your realm to something",
                    "${CC.GRAY}different.",
                    "",
                    "${CC.YELLOW}Click to configure!"
                ).toButton { _, _ ->
                    CallbackInputPrompt("${CC.GREEN}Enter a new name for your realm! It must be only letters or numbers, and not contain spaces:") {
                        if (!it.matches(Regex("^[a-zA-Z0-9]+$")))
                        {
                            player.sendMessage("${CC.RED}This name is not allowed. Please make sure it is only letters or numbers!")
                            return@CallbackInputPrompt
                        }

                        if (ChatMessageFilterHandler.handleMessageFilter(player, it, reportToStaff = false))
                        {
                            player.sendMessage("${CC.RED}This name is not allowed. Please make sure it is appropriate!")
                            return@CallbackInputPrompt
                        }

                        if (it.length > 16)
                        {
                            player.sendMessage("${CC.RED}Your realm name must be less than 16 characters!")
                            return@CallbackInputPrompt
                        }

                        PlayerHousingService.findByName(it.lowercase()).thenAccept { foundHouse ->
                            if (foundHouse != null)
                            {
                                player.sendMessage("${CC.RED}A realm with this name already exists! Please try again later.")
                                return@thenAccept
                            }

                            house.name = it.lowercase()
                            house.save().join()

                            player.sendMessage("${CC.B_GREEN}SUCCESS! ${CC.GREEN}Your realm name has been updated to ${CC.WHITE}${it}")

                            openMenu(player)
                            Button.playNeutral(player)
                        }
                    }.start(player)
                }

            buttons[41] = ItemBuilder.of(XMaterial.GOLDEN_APPLE)
                .name("${CC.GREEN}Realm Display Name")
                .addToLore(
                    "${CC.GRAY}Allows you to change the",
                    "${CC.GRAY}display name of your realm to",
                    "${CC.GRAY}something different.",
                    "",
                    "${CC.YELLOW}You can use color codes for this!",
                    "",
                    "${CC.YELLOW}Click to configure!"
                ).toButton { _, _ ->
                    CallbackInputPrompt("${CC.GREEN}Enter a display name for your realm! (16 character maximum, can use colors):") {
                        if (it.length > 16)
                        {
                            player.sendMessage("${CC.RED}Your realm name must be less than 16 characters!")
                            return@CallbackInputPrompt
                        }

                        if (ChatMessageFilterHandler.handleMessageFilter(player, it, reportToStaff = false))
                        {
                            player.sendMessage("${CC.RED}This name is not allowed. Please make sure it is appropriate!")
                            return@CallbackInputPrompt
                        }

                        house.displayName = it.translateCC()
                        house.save()

                        player.sendMessage("${CC.B_GREEN}SUCCESS! ${CC.GREEN}Your display name has been updated to ${CC.WHITE}${it.translateCC()}")

                        openMenu(player)
                        Button.playNeutral(player)
                    }.start(player)
                }

            buttons[42] = ItemBuilder.of(XMaterial.NAME_TAG)
                .name("${CC.GREEN}Realm Tags")
                .addToLore(
                    "${CC.GRAY}Allows you to change the",
                    "${CC.GRAY}tags of your realm to help",
                    "${CC.GRAY}them it get discovered.",
                    "",
                    "${CC.YELLOW}Click to configure!"
                ).toButton { _, _ ->
                    Button.playNeutral(player)
                    HouseTagsEditorMenu(house).openMenu(player)
                }

            buttons[44] = ItemBuilder.of(XMaterial.JUKEBOX)
                .name("${CC.GREEN}Music Settings")
                .addToLore(
                    "${CC.GRAY}Allows you to change and",
                    "${CC.GRAY}view your music settings for",
                    "${CC.GRAY}this realm.",
                    "",
                    "${CC.YELLOW}Click to change music!"
                ).toButton { _, _ ->
                    Button.playNeutral(player)
                    HouseMusicSelectionMenu(house).openMenu(player)
                }
        } else
        {

        }

        return buttons
    }
}

private fun GameMode.prettyName(): String =
    name.lowercase().replaceFirstChar(Char::uppercase)
