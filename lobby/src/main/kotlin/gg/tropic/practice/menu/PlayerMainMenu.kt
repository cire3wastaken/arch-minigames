package gg.tropic.practice.menu

import gg.tropic.practice.parkour.ParkourService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 12/17/2023
 */
class PlayerMainMenu : Menu("Navigator")
{
    init
    {
        placeholder = true
    }

    override fun size(buttons: Map<Int, Button>) = 27
    override fun getButtons(player: Player) = mapOf(
        9 to ItemBuilder
            .of(Material.GRASS)
            .name("${CC.GOLD}Spawn")
            .addToLore(
                "${CC.GRAY}Teleport back to spawn!",
                "",
                "${CC.GREEN}Click to teleport!"
            )
            .toButton { _, _ ->
                player.performCommand("spawn")
            },
        11 to ItemBuilder
            .of(Material.BLAZE_POWDER)
            .name("${CC.GOLD}Cosmetics")
            .addToLore(
                "${CC.GRAY}Customize everything about",
                "${CC.GRAY}your in-game profile using",
                "${CC.GRAY}the cosmetics menu!",
                "",
                "${CC.GREEN}Click to open!"
            )
            .toButton { _, _ ->
                player.performCommand("cosmetics")
            },
        13 to ItemBuilder
            .of(Material.FIREBALL)
            .name("${CC.GREEN}Statistics")
            .addToLore(
                "${CC.GRAY}View practice statistics",
                "${CC.GRAY}in all categories!",
                "",
                "${CC.GREEN}Click to open!"
            )
            .toButton { _, _ ->
                player.performCommand("statistics")
            },
        15 to ItemBuilder
            .of(Material.LEASH)
            .name("${CC.D_PURPLE}Arcade")
            .addToLore(
                "${CC.GRAY}Jump into the Arcade!",
                "",
                "${CC.GRAY}Games include:",
                "${CC.WHITE}- Sumo, OITC, Red Light Green Light",
                "",
                "${CC.GREEN}Click to play!"
            )
            .toButton { _, _ ->
                player.performCommand("joinqueue arcadelobby")
            },
        17 to ItemBuilder
            .of(Material.LADDER)
            .name("${CC.YELLOW}Parkour")
            .addToLore(
                "${CC.GRAY}Navigate to the parkour",
                "${CC.GRAY}area! Try to get on the",
                "${CC.GRAY}leaderboards!",
                "",
                *ParkourService.topTenLeaderboardEntries.toTypedArray(),
                "",
                "${CC.GREEN}Click to teleport!"
            )
            .toButton { _, _ ->
                player.performCommand("parkour")
            },
    )
}
