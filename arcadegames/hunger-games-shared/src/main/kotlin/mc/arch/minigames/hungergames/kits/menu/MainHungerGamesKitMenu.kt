package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.kits.HungerGamesKit
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import mc.arch.minigames.hungergames.profile.HungerGamesProfile
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player

/**
 * Kit shop menu for purchasing kit levels.
 *
 * @author ArchMC
 */
class MainHungerGamesKitMenu : Menu("Kit Shop")
{
    override fun size(buttons: Map<Int, Button>) = 54

    init
    {
        placeholder = true
        shouldLoadInSync()
    }

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf(
            4 to ItemBuilder
                .of(XMaterial.BOOK)
                .name("${CC.GREEN}Kit Shop")
                .addToLore(
                    "${CC.GRAY}You are viewing Survival",
                    "${CC.GRAY}Games kits!",
                    "",
                    "${CC.GRAY}Each kit has multiple levels.",
                    "${CC.GRAY}Higher levels give better gear.",
                    "${CC.GRAY}Purchase levels to unlock them!",
                    "",
                    "${CC.I_WHITE}Click a kit to view and",
                    "${CC.I_WHITE}purchase levels!"
                )
                .toButton()
        )

        val profile = HungerGamesProfileService.find(player)
        val slots = (10..16) + (19..25) + (28..34)
        val kits = HungerGamesKitDataSync.cached().kits.values.toList()

        slots.withIndex().forEach { slot ->
            val kit = kits.getOrNull(slot.index)
                ?: return@forEach

            val highestOwned = profile?.highestOwnedLevel(kit.id, kit.purchasableMaxLevel()) ?: 1
            val totalLevels = kit.levels.keys.count { it <= HungerGamesKit.PURCHASABLE_MAX_LEVEL }
            val killReq = HungerGamesProfile.killRequirement(kit.id)
            val meetsKillReq = profile?.meetsKillRequirement(kit.id) ?: (killReq <= 0L)

            // Find the next level the player can buy (excluding prestige level)
            val nextUnownedLevel = kit.levels.entries
                .filter { it.key <= HungerGamesKit.PURCHASABLE_MAX_LEVEL }
                .sortedBy { it.key }
                .firstOrNull { profile?.hasKit(kit.id, it.key) != true }

            buttons[slot.value] = runCatching {
                ItemBuilder.copyOf(kit.icon)
            }.getOrElse {
                ItemBuilder.of(XMaterial.BARRIER)
            }
                .name(
                    if (killReq > 0L && !meetsKillReq) "${CC.RED}${kit.displayName} ${CC.GRAY}(Locked)"
                    else "${CC.GREEN}${kit.displayName}"
                )
                .addToLore(
                    "${CC.GRAY}Levels Owned: ${CC.WHITE}$highestOwned${CC.GRAY}/${CC.WHITE}$totalLevels",
                )
                .apply {
                    if (killReq > 0L)
                    {
                        addToLore(
                            "",
                            if (meetsKillReq) "${CC.GREEN}✔ Kill requirement met!"
                            else "${CC.RED}✖ Requires ${CC.YELLOW}${Numbers.format(killReq)} kills"
                        )
                    }

                    if (meetsKillReq || killReq <= 0L)
                    {
                        if (nextUnownedLevel != null)
                        {
                            addToLore(
                                "",
                                "${CC.GRAY}Next Level: ${CC.WHITE}Lv.${nextUnownedLevel.key}",
                                "${CC.GRAY}Cost: ${CC.D_PURPLE}${Numbers.format(nextUnownedLevel.value.price)} Arcade Coins"
                            )
                        } else
                        {
                            addToLore(
                                "",
                                "${CC.GREEN}✔ All levels unlocked!"
                            )
                        }
                    }
                }
                .addToLore(
                    "",
                    "${CC.YELLOW}Click to view levels!"
                )
                .toButton { _, _ ->
                    ViewKitContentsMenu(kit).openMenu(player)
                }
        }

        return buttons
    }
}
