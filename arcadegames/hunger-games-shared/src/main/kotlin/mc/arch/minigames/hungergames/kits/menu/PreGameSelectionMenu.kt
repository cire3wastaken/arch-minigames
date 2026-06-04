package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import mc.arch.minigames.hungergames.profile.HungerGamesProfile
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player

class PreGameSelectionMenu : Menu("Select a Kit...")
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
                .name("${CC.GREEN}Kit Selection")
                .addToLore(
                    "${CC.GRAY}Select a kit to use",
                    "${CC.GRAY}in this game!",
                    "",
                    "${CC.GRAY}Each kit has multiple levels.",
                    "${CC.GRAY}Higher levels give better gear.",
                    "",
                    "${CC.I_WHITE}Click a kit to select it!"
                )
                .toButton()
        )

        val profile = HungerGamesProfileService.find(player)
        val slots = (10..16) + (19..25) + (28..34)
        val kits = HungerGamesKitDataSync.cached().kits.values.toList()

        slots.withIndex().forEach { slot ->
            val kit = kits.getOrNull(slot.index)
                ?: return@forEach

            val isSelected = profile?.selectedKit == kit.id
            val highestOwned = profile?.highestOwnedLevel(kit.id, kit.purchasableMaxLevel()) ?: 1
            val isPrestiged = (profile?.getPrestige(kit.id) ?: 0) >= 1
            val killReq = HungerGamesProfile.killRequirement(kit.id)
            val meetsKillReq = profile?.meetsKillRequirement(kit.id) ?: (killReq <= 0L)
            val isLocked = killReq > 0L && !meetsKillReq

            buttons[slot.value] = runCatching {
                ItemBuilder.copyOf(kit.icon)
            }.getOrElse {
                ItemBuilder.of(XMaterial.BARRIER)
            }
                .name(
                    if (isLocked) "${CC.RED}${kit.displayName} ${CC.GRAY}(Locked)"
                    else "${CC.GREEN}${kit.displayName}"
                )
                .addToLore(
                    if (isPrestiged) "${CC.GRAY}Your Level: ${CC.GOLD}✦ Prestige"
                    else "${CC.GRAY}Your Level: ${CC.WHITE}$highestOwned",
                )
                .apply {
                    if (isLocked)
                    {
                        addToLore(
                            "",
                            "${CC.RED}✖ Requires ${CC.YELLOW}${Numbers.format(killReq)} kills",
                            "${CC.GRAY}Your Kills: ${CC.WHITE}${Numbers.format(profile?.totalKills ?: 0L)}"
                        )
                    } else if (isSelected)
                    {
                        addToLore(
                            "",
                            "${CC.GREEN}✔ Currently Selected",
                            "${CC.GRAY}Level: ${CC.WHITE}${profile?.selectedKitLevel ?: 1}"
                        )
                    }
                }
                .addToLore(
                    "",
                    when {
                        isLocked -> "${CC.RED}Locked!"
                        isSelected -> "${CC.GREEN}Currently selected!"
                        else -> "${CC.YELLOW}Click to select!"
                    }
                )
                .toButton { _, _ ->
                    val prof = HungerGamesProfileService.find(player)
                        ?: return@toButton

                    if (!prof.meetsKillRequirement(kit.id))
                    {
                        Button.playFail(player)
                        player.sendMessage(
                            "${CC.RED}You need ${CC.GOLD}${Numbers.format(killReq)} kills${CC.RED} to unlock this kit!"
                        )
                        return@toButton
                    }

                    Button.playNeutral(player)

                    val selectLevel = prof.highestOwnedLevel(kit.id, kit.purchasableMaxLevel())
                    prof.selectedKit = kit.id
                    prof.selectedKitLevel = selectLevel
                    prof.save()

                    player.sendMessage(
                        "${CC.GREEN}You selected the ${CC.GOLD}${kit.displayName}${CC.GREEN} kit at level ${CC.GOLD}$selectLevel${CC.GREEN}!"
                    )
                    player.closeInventory()
                }
        }

        return buttons
    }
}
