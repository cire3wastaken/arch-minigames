package gg.tropic.practice.menu.manage.bezierteleporters

import com.cryptomorin.xseries.XMaterial
import gg.scala.commons.configurable.editBounds
import gg.scala.commons.configurable.editEnum
import gg.scala.commons.configurable.editInt
import gg.scala.commons.configurable.editPosition
import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.configuration.minigame.MotionPreset
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
class ManageBezierTeleporterMenu(
    private val index: Int
) : Menu("Managing Bezier Teleporter #${index + 1}")
{
    init
    {
        placeholder = true
    }

    override fun getButtons(player: Player) = mapOf(
        10 to editBounds(
            PracticeConfigurationService,
            title = "Start Trigger Bounds",
            material = XMaterial.EMERALD,
            getter = {
                local().bezierTeleporters[index].start
            },
            setter = {
                local().bezierTeleporters[index].start = it
            }
        ),
        11 to editPosition(
            PracticeConfigurationService,
            title = "End Pose",
            material = XMaterial.DIAMOND,
            getter = {
                local().bezierTeleporters[index].end
            },
            setter = {
                local().bezierTeleporters[index].end = it
            }
        ),
        12 to editInt(
            PracticeConfigurationService,
            title = "Max Height Gain",
            material = XMaterial.ARROW,
            range = 1..20,
            getter = {
                local().bezierTeleporters[index].height
            },
            setter = {
                local().bezierTeleporters[index].height = it
            }
        ),
        13 to editInt(
            PracticeConfigurationService,
            title = "Travel Duration",
            material = XMaterial.FEATHER,
            range = 10..100,
            getter = {
                local().bezierTeleporters[index].duration
            },
            setter = {
                local().bezierTeleporters[index].duration = it
            }
        ),
        14 to editEnum<MotionPreset, _>(
            PracticeConfigurationService,
            title = "Trapezoidal Motion Profile Preset",
            material = XMaterial.GOLD_INGOT,
            getter = {
                local().bezierTeleporters[index].preset
            },
            setter = {
                local().bezierTeleporters[index].preset = it as MotionPreset
            }
        ),
        16 to RemoveButton {
            PracticeConfigurationService.editAndSave {
                local().bezierTeleporters.removeAt(index)
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
                ViewBezierTeleportersMenu().openMenu(player)
            }
        }
    }

    override fun size(buttons: Map<Int, Button>) = 27
}
