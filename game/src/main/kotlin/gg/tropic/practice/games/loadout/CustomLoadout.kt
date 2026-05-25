package gg.tropic.practice.games.loadout

import gg.tropic.practice.kit.Kit
import gg.tropic.practice.profile.loadout.Loadout
import gg.tropic.practice.extensions.deepClone
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 9/25/2023
 */
class CustomLoadout(
    private val loadout: Loadout,
    private val kit: Kit
) : SelectedLoadout
{
    override fun displayName() = loadout.name
    override fun apply(player: Player)
    {
        kit.populate(player)
        if (loadout.inventoryContents.any { it != null })
        {
            player.inventory.contents = loadout.inventoryContents.deepClone()
        }
        player.fixInventoryBasedOnRBTeamColor()
        player.updateInventory()
    }
}
