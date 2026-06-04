package gg.tropic.practice.menu.manage.npc

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.configurable.editPosition
import gg.scala.commons.configurable.editString
import gg.tropic.practice.configuration.PracticeConfigurationService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.menu.buttons.RemoveButton
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.Tasks
import org.bukkit.entity.Player

/**
 * @author Subham
 * @since 6/27/25
 */
class ManageMinigamePlayNPCMenu(
    private val index: Int
) : Menu("Managing Play NPC #${index + 1}")
{
    init
    {
        placeholder = true
    }

    override fun getButtons(player: Player) = mapOf(
        10 to editPosition(
            PracticeConfigurationService,
            title = "Spawn Location",
            material = XMaterial.EMERALD,
            getter = {
                local().playNPCs[index].position
            },
            setter = {
                local().playNPCs[index].position = it
            }
        ),
        11 to editString(
            PracticeConfigurationService,
            title = "Associated Game Mode(s)",
            material = XMaterial.NAME_TAG,
            getter = {
                local().playNPCs[index].associatedGameMode
            },
            setter = {
                local().playNPCs[index].associatedGameMode = it
            }
        ),
        13 to editString(
            PracticeConfigurationService,
            title = "Group Display Name",
            material = XMaterial.OAK_SIGN,
            getter = {
                local().playNPCs[index].displayName
            },
            setter = {
                local().playNPCs[index].displayName = it
            }
        ),
        16 to RemoveButton {
            PracticeConfigurationService.editAndSave {
                local().playNPCs.removeAt(index)
            }

            player.sendMessage("${CC.RED}Removed!")
            player.closeInventory()
        }
    )

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Tasks.sync {
                ViewMinigameNPCsMenu().openMenu(player)
            }
        }
    }

    override fun size(buttons: Map<Int, Button>) = 27
}
