package mc.arch.minigames.persistent.housing.game.menu.house.settings

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.persistent.housing.api.content.HousingGameMode
import mc.arch.minigames.persistent.housing.api.content.HousingTime
import mc.arch.minigames.persistent.housing.api.formatName
import mc.arch.minigames.persistent.housing.api.model.PlayerHouse
import mc.arch.minigames.persistent.housing.api.model.VisitationStatus
import mc.arch.minigames.persistent.housing.game.menu.house.MainHouseMenu
import mc.arch.minigames.persistent.housing.game.spatial.SpatialZoneService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.entity.Player
import kotlin.collections.set

class HouseSettingsMenu(val house: PlayerHouse): Menu("House Settings")
{
    init
    {
        updateAfterClick = true
    }

    override fun size(buttons: Map<Int, Button>): Int = 36

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()

        buttons[10] = ItemBuilder.of(XMaterial.PLAYER_HEAD)
            .name("${CC.GREEN}Max Players: ${CC.AQUA}${house.maxPlayers}")
            .addToLore(
                "${CC.GRAY}Change the maximum number of",
                "${CC.GRAY}players allowed in your realm",
                "${CC.GRAY}at one time.",
                "",
                "${CC.B_RED}WARNING ${CC.RED}High player counts could",
                "${CC.RED}impact the performance of your",
                "${CC.RED}house",
                "",
                "${CC.GREEN}Left-Click to cycle forward +10",
                "${CC.RED}Right-Click to cycle backward +10"
            ).toButton { _, click ->
                if (click?.isLeftClick == true)
                {
                    house.maxPlayers += 10

                    if (house.maxPlayers >= 200)
                    {
                        house.maxPlayers = 200
                    }
                } else
                {
                    house.maxPlayers -= 10

                    if (house.maxPlayers <= 10)
                    {
                        house.maxPlayers = 10
                    }
                }

                Button.playNeutral(player)
                house.save()
            }

        buttons[11] = ItemBuilder.of(XMaterial.ANVIL)
            .name("${CC.GREEN}Border: ${CC.AQUA}${house.plotSizeBlocks}x${house.plotSizeBlocks}")
            .addToLore(
                "${CC.GRAY}Change the maximum distance",
                "${CC.GRAY}that players can venture out",
                "${CC.GRAY}from your realm.",
                "",
                "${CC.YELLOW}Distance Caps:",
                "${CC.GRAY}Default: ${CC.WHITE}400x400",
                "${CC.PINK}Mythic${CC.GRAY}: ${CC.WHITE}600x600",
                "${CC.AQUA}Majestic${CC.GRAY}: ${CC.WHITE}850x850",
                "${CC.D_AQUA}Champion${CC.GRAY}: ${CC.WHITE}1200x1200",
                "",
                "${CC.GREEN}Left-Click to cycle forward +10",
                "${CC.RED}Right-Click to cycle backward +10"
            ).toButton { _, click ->
                if (click?.isLeftClick == true)
                {
                    house.plotSizeBlocks += 10

                    if (house.plotSizeBlocks >= 400 && !player.hasPermission("housing.plot.500"))
                    {
                        house.plotSizeBlocks = 400
                    }

                    if (house.plotSizeBlocks >= 600 && !player.hasPermission("housing.plot.750"))
                    {
                        house.plotSizeBlocks = 600
                    }

                    if (house.plotSizeBlocks >= 850 && !player.hasPermission("housing.plot.1000"))
                    {
                        house.plotSizeBlocks = 850
                    }

                    if (house.plotSizeBlocks >= 1200)
                    {
                        house.plotSizeBlocks = 1200
                    }
                } else
                {
                    house.plotSizeBlocks -= 10

                    if (house.plotSizeBlocks <= 100)
                    {
                        house.plotSizeBlocks = 100
                    }
                }

                SpatialZoneService.applyWorldBorder(house, player.world)

                Button.playNeutral(player)
                house.save()
            }

        buttons[12] = ItemBuilder.of(XMaterial.WOODEN_AXE)
            .name("${CC.GREEN}Realm GameMode: ${CC.AQUA}${house.defaultGamemode.name}")
            .addToLore(
                "${CC.GRAY}Change the gamemode that",
                "${CC.GRAY}players will spawn in",
                "${CC.GRAY}at one time.",
                "",
                "${CC.GREEN}Click to change gamemode",
            ).toButton { _, _ ->
                val currentIndex = HousingGameMode.entries.indexOf(house.defaultGamemode)
                val next = HousingGameMode.entries.getOrElse(currentIndex + 1) { HousingGameMode.SURVIVAL }

                house.defaultGamemode = next
                house.save()

                Button.playNeutral(player)
                player.sendMessage("${CC.YELLOW}Your realm gamemode has been updated to: ${CC.GREEN}${next.name}")
            }

        buttons[31] = MainHouseMenu.mainMenuButton(house)

        return buttons
    }
}
