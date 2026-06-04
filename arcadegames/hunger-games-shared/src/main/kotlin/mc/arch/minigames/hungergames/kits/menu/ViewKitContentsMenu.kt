package mc.arch.minigames.hungergames.kits.menu

import com.cryptomorin.xseries.XMaterial
import gg.tropic.game.extensions.economy.Accounts
import gg.tropic.game.extensions.economy.EconomyDataSync
import gg.tropic.game.extensions.economy.EconomyProfileService
import gg.tropic.game.extensions.economy.Transaction
import gg.tropic.game.extensions.economy.TransactionResult
import gg.tropic.game.extensions.economy.TransactionService
import gg.tropic.game.extensions.economy.TransactionType
import gg.tropic.practice.menu.BukkitInventoryCallback
import gg.tropic.practice.menu.CallbackButton
import gg.tropic.practice.menu.InventoryEventsListener
import mc.arch.minigames.hungergames.kits.HungerGamesKit
import mc.arch.minigames.hungergames.profile.HungerGamesProfile
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import me.lucko.helper.Schedulers
import net.evilblock.cubed.menu.Button
import net.evilblock.cubed.menu.Menu
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.ItemBuilder
import net.evilblock.cubed.util.math.Numbers
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * @author ArchMC
 */
class ViewKitContentsMenu(
    private val kit: HungerGamesKit
) : Menu("${kit.displayName} - Kit Contents")
{
    companion object
    {
        private const val ECONOMY_ID = "arcade-coins"
    }

    init
    {
        placeholder = true
        shouldLoadInSync()
    }

    override fun size(buttons: Map<Int, Button>) = 45

    override fun getButtons(player: Player): Map<Int, Button>
    {
        val buttons = mutableMapOf<Int, Button>()
        val profile = HungerGamesProfileService.find(player)
        val killRequirement = HungerGamesProfile.killRequirement(kit.id)
        val meetsKillReq = profile?.meetsKillRequirement(kit.id) ?: (killRequirement <= 0L)

        // Get economy info for balance display
        val economy = EconomyDataSync.cached().economies[ECONOMY_ID]
        val economyProfile = EconomyProfileService.find(player)
        val balance = economyProfile?.balance(ECONOMY_ID) ?: 0L

        // Header info item
        buttons[4] = ItemBuilder
            .of(XMaterial.BOOK)
            .name("${CC.GREEN}${kit.displayName}")
            .addToLore(
                "${CC.GRAY}Viewing kit contents for",
                "${CC.GRAY}each level of this kit.",
                "",
                "${CC.GRAY}Levels: ${CC.WHITE}${kit.levels.keys.count { it <= HungerGamesKit.PURCHASABLE_MAX_LEVEL }}",
                "",
                "${CC.GRAY}Your Balance: ${
                    economy?.format(balance) ?: "${CC.D_PURPLE}${Numbers.format(balance)} Arcade Coins"
                }",
            )
            .apply {
                if (killRequirement > 0L)
                {
                    addToLore(
                        "",
                        if (meetsKillReq) "${CC.GREEN}✔ Kill requirement met!"
                        else "${CC.RED}✖ Requires ${CC.YELLOW}${Numbers.format(killRequirement)} kills ${CC.RED}to unlock",
                        "${CC.GRAY}Your Kills: ${CC.WHITE}${Numbers.format(profile?.totalKills ?: 0L)}"
                    )
                }
            }
            .addToLore(
                "",
                "${CC.I_WHITE}Click a level to purchase it!"
            )
            .toButton()

        // Level buttons (only purchasable levels — prestige level is reserved)
        val slots = (10..16) + (19..25) + (28..34)
        val sortedLevels = kit.levels.entries
            .filter { it.key <= HungerGamesKit.PURCHASABLE_MAX_LEVEL }
            .sortedBy { it.key }

        sortedLevels.forEachIndexed { index, (level, kitLevel) ->
            if (index >= slots.size) return@forEachIndexed

            val isOwned = profile?.hasKit(kit.id, level) ?: (level <= 1)
            val price = kitLevel.price

            buttons[slots[index]] = runCatching {
                ItemBuilder.copyOf(kit.icon)
            }.getOrElse {
                ItemBuilder.of(XMaterial.BARRIER)
            }
                .name(
                    if (isOwned) "${CC.GREEN}Level $level"
                    else "${CC.RED}Level $level ${CC.GRAY}(Locked)"
                )
                .amount(level.coerceIn(1, 64))
                .apply {
                    val loreLines = mutableListOf<String>()

                    // Show ownership status
                    if (isOwned)
                    {
                        loreLines.add("${CC.GREEN}✔ Owned")
                        loreLines.add("")
                    } else
                    {
                        loreLines.add("${CC.GRAY}Price: ${CC.D_PURPLE}${Numbers.format(price)} Arcade Coins")
                        if (balance >= price)
                        {
                            loreLines.add("${CC.GREEN}You can afford this!")
                        } else
                        {
                            loreLines.add("${CC.RED}You need ${CC.D_PURPLE}${Numbers.format(price - balance)} ${CC.RED}more Arcade Coins!")
                        }
                        loreLines.add("")
                    }

                    // Show armor contents
                    val armorNames = listOf("Helmet", "Chestplate", "Leggings", "Boots")
                    val armorItems = kitLevel.armor
                        .reversed()
                        .mapIndexed { i, item -> armorNames[i] to item }
                        .filter { it.second != null && it.second!!.type != XMaterial.AIR.parseMaterial() }

                    if (armorItems.isNotEmpty())
                    {
                        loreLines.add("${CC.AQUA}Armor:")
                        armorItems.forEach { (slot, item) ->
                            loreLines.add("${CC.GRAY}  $slot: ${CC.WHITE}${formatItemName(item!!)}")
                        }
                        loreLines.add("")
                    }

                    // Show inventory contents
                    val inventoryItems = kitLevel.inventory
                        .filterNotNull()
                        .filter { it.type != XMaterial.AIR.parseMaterial() }

                    if (inventoryItems.isNotEmpty())
                    {
                        loreLines.add("${CC.YELLOW}Items:")
                        val grouped = inventoryItems.groupBy { formatItemName(it) }
                        grouped.forEach { (name, items) ->
                            val totalAmount = items.sumOf { it.amount }
                            if (totalAmount > 1)
                            {
                                loreLines.add("${CC.GRAY}  ${CC.WHITE}${name} ${CC.GRAY}x$totalAmount")
                            } else
                            {
                                loreLines.add("${CC.GRAY}  ${CC.WHITE}${name}")
                            }
                        }
                        loreLines.add("")
                    }

                    if (armorItems.isEmpty() && inventoryItems.isEmpty())
                    {
                        loreLines.add("${CC.RED}No items configured")
                        loreLines.add("")
                    }

                    // Click action lore
                    if (!isOwned)
                    {
                        loreLines.add("${CC.GOLD}Click to purchase for ${CC.D_PURPLE}${Numbers.format(price)} Arcade Coins${CC.GOLD}!")
                    }

                    setLore(loreLines)
                }
                .toButton { _, _ ->
                    val prof = HungerGamesProfileService.find(player)
                        ?: return@toButton

                    if (prof.hasKit(kit.id, level))
                    {
                        // Already owned — nothing to do here
                        return@toButton
                    }

                    // Check kill requirement
                    if (!prof.meetsKillRequirement(kit.id))
                    {
                        val required = HungerGamesProfile.killRequirement(kit.id)
                        Button.playFail(player)
                        player.sendMessage(
                            "${CC.RED}You need ${CC.GOLD}${Numbers.format(required)} kills${CC.RED} to unlock this kit! You have ${CC.GOLD}${Numbers.format(prof.totalKills)}${CC.RED}."
                        )
                        return@toButton
                    }

                    // Need to purchase
                    val currentBalance = EconomyProfileService.find(player)
                        ?.balance(ECONOMY_ID) ?: 0L

                    if (currentBalance < price)
                    {
                        Button.playFail(player)
                        player.sendMessage(
                            "${CC.RED}You don't have enough Arcade Coins! You need ${CC.D_PURPLE}${
                                Numbers.format(price - currentBalance)
                            }${CC.RED} more Arcade Coins."
                        )
                        return@toButton
                    }

                    // Check previous levels are owned
                    val previousLevel = level - 1
                    if (previousLevel > 1 && !prof.hasKit(kit.id, previousLevel))
                    {
                        Button.playFail(player)
                        player.sendMessage(
                            "${CC.RED}You must purchase level ${CC.GOLD}$previousLevel${CC.RED} first!"
                        )
                        return@toButton
                    }

                    // Submit purchase transaction
                    TransactionService.submit(
                        Transaction(
                            sender = player.uniqueId,
                            receiver = Accounts.SERVER,
                            type = TransactionType.Purchase,
                            economy = ECONOMY_ID,
                            amount = price
                        )
                    ).thenAccept { result ->
                        if (result != TransactionResult.Success)
                        {
                            player.sendMessage("${CC.RED}Transaction failed! Please try again.")
                            return@thenAccept
                        }

                        // Unlock the kit level
                        prof.unlockKit(kit.id, level)
                        prof.save()

                        Button.playNeutral(player)
                        player.sendMessage(
                            "${CC.GREEN}You purchased ${CC.GOLD}${kit.displayName} Level $level${CC.GREEN} for ${CC.BD_PURPLE}${
                                Numbers.format(price)
                            } Arcade Coins${CC.GREEN}!"
                        )

                        // Refresh menu
                        Schedulers.sync().runLater({
                            ViewKitContentsMenu(kit).openMenu(player)
                        }, 1L)
                    }
                }
        }

        // Kit Stats button
        val stats = profile?.getStatsFor(kit.id)
        val kdr = if ((stats?.deaths ?: 0L) > 0)
            String.format("%.2f", (stats?.kills?.toDouble() ?: 0.0) / stats!!.deaths)
        else if ((stats?.kills ?: 0L) > 0) "∞" else "N/A"

        buttons[29] = ItemBuilder
            .of(XMaterial.PAPER)
            .name("${CC.B_GREEN}Kit Statistics")
            .addToLore(
                "${CC.GRAY}Your stats with ${CC.WHITE}${kit.displayName}${CC.GRAY}:",
                "",
                "${CC.GRAY}Kills: ${CC.WHITE}${Numbers.format(stats?.kills ?: 0L)}",
                "${CC.GRAY}Deaths: ${CC.WHITE}${Numbers.format(stats?.deaths ?: 0L)}",
                "${CC.GRAY}K/D Ratio: ${CC.WHITE}$kdr",
                "",
                "${CC.GRAY}Games Played: ${CC.WHITE}${Numbers.format(stats?.gamesPlayed ?: 0L)}",
                "",
                "${CC.GRAY}Damage Dealt: ${CC.WHITE}${Numbers.format(stats?.damageDealt?.toLong() ?: 0L)}",
                "${CC.GRAY}Damage Taken: ${CC.WHITE}${Numbers.format(stats?.damageTaken?.toLong() ?: 0L)}"
            )
            .toButton()

        // Edit Loadout button
        val highestOwned = profile?.highestOwnedLevel(kit.id, kit.purchasableMaxLevel()) ?: 1
        val hasCustomLoadout = profile?.customLoadouts?.containsKey(kit.id) == true

        buttons[31] = ItemBuilder
            .of(XMaterial.ANVIL)
            .name("${CC.AQUA}Edit Loadout")
            .addToLore(
                "${CC.GRAY}Rearrange your kit's inventory",
                "${CC.GRAY}items to your liking!",
                "",
                "${CC.GRAY}Armor cannot be changed.",
                "",
                if (hasCustomLoadout) "${CC.GREEN}✔ Custom loadout saved!"
                else "${CC.GRAY}Using default layout.",
                "",
                "${CC.YELLOW}Click to edit!"
            )
            .toButton { _, _ ->
                val prof = HungerGamesProfileService.find(player)
                    ?: return@toButton

                val kitLevel = kit.levels[highestOwned] ?: return@toButton
                val defaultInventory = kitLevel.inventory.map { it?.clone() }.toTypedArray()

                // Use existing custom loadout or default inventory
                val currentLoadout = prof.customLoadouts[kit.id]
                    ?: defaultInventory

                val isSelected = prof.selectedKit == kit.id

                // Bottom row slots (36-44) are all immutable
                val bottomRowSlots = (36..44).toList()

                // Build action buttons on the bottom row
                val actionButtons = mutableMapOf<Int, CallbackButton>()

                // Slot 36: Select / Deselect Kit
                actionButtons[36] = CallbackButton(
                    item = ItemBuilder
                        .of(if (isSelected) XMaterial.RED_DYE else XMaterial.LIME_DYE)
                        .name(
                            if (isSelected) "${CC.RED}Deselect Kit"
                            else "${CC.GREEN}Select Kit"
                        )
                        .addToLore(
                            if (isSelected) "${CC.GRAY}Click to deselect this kit."
                            else "${CC.GRAY}Click to select this kit."
                        )
                        .build(),
                    onClick = { p ->
                        val pr = HungerGamesProfileService.find(p) ?: return@CallbackButton

                        if (pr.selectedKit == kit.id)
                        {
                            pr.selectedKit = null
                            pr.selectedKitLevel = 1
                            pr.save()
                            p.sendMessage("${CC.RED}You deselected the ${CC.GOLD}${kit.displayName}${CC.RED} kit.")
                        } else
                        {
                            val selectLevel = pr.highestOwnedLevel(kit.id, kit.purchasableMaxLevel())
                            pr.selectedKit = kit.id
                            pr.selectedKitLevel = selectLevel
                            pr.save()
                            p.sendMessage("${CC.GREEN}You selected the ${CC.GOLD}${kit.displayName}${CC.GREEN} kit at level ${CC.GOLD}$selectLevel${CC.GREEN}!")
                        }

                        // Skip the close callback so it doesn't save, then reopen
                        val cb = InventoryEventsListener.inventoryMap[p.uniqueId]
                        cb?.skipCloseCallback = true
                        p.closeInventory()

                        Schedulers.sync().runLater({
                            ViewKitContentsMenu(kit).openMenu(p)
                        }, 1L)
                    }
                )

                // Slot 38: Save Layout
                actionButtons[38] = CallbackButton(
                    item = ItemBuilder
                        .of(XMaterial.WRITABLE_BOOK)
                        .name("${CC.GREEN}Save Layout")
                        .addToLore(
                            "${CC.GRAY}Click to save your custom",
                            "${CC.GRAY}loadout and close the editor."
                        )
                        .build(),
                    onClick = { p ->
                        val pr = HungerGamesProfileService.find(p) ?: return@CallbackButton

                        // Grab the current inventory contents (only the 36 item slots)
                        val cb = InventoryEventsListener.inventoryMap[p.uniqueId]
                        val inv = p.openInventory.topInventory
                        val contents = Array<ItemStack?>(36) { i -> inv.getItem(i) }

                        pr.customLoadouts[kit.id] = contents
                        pr.save()

                        p.sendMessage("${CC.GREEN}Your custom loadout for ${CC.GOLD}${kit.displayName}${CC.GREEN} has been saved!")

                        cb?.skipCloseCallback = true
                        p.closeInventory()

                        Schedulers.sync().runLater({
                            ViewKitContentsMenu(kit).openMenu(p)
                        }, 1L)
                    }
                )

                // Slot 40: Reset Layout
                actionButtons[40] = CallbackButton(
                    item = ItemBuilder
                        .of(XMaterial.BARRIER)
                        .name("${CC.RED}Reset Layout")
                        .addToLore(
                            "${CC.GRAY}Click to reset your loadout",
                            "${CC.GRAY}to the default kit items."
                        )
                        .build(),
                    onClick = { p ->
                        val pr = HungerGamesProfileService.find(p) ?: return@CallbackButton

                        pr.customLoadouts.remove(kit.id)
                        pr.save()

                        p.sendMessage("${CC.YELLOW}Your loadout for ${CC.GOLD}${kit.displayName}${CC.YELLOW} has been reset to default!")

                        val cb = InventoryEventsListener.inventoryMap[p.uniqueId]
                        cb?.skipCloseCallback = true
                        p.closeInventory()

                        Schedulers.sync().runLater({
                            ViewKitContentsMenu(kit).openMenu(p)
                        }, 1L)
                    }
                )

                // Slot 44: Back (no save)
                actionButtons[44] = CallbackButton(
                    item = ItemBuilder
                        .of(XMaterial.ARROW)
                        .name("${CC.RED}Back")
                        .addToLore(
                            "${CC.GRAY}Click to go back without",
                            "${CC.GRAY}saving your changes."
                        )
                        .build(),
                    onClick = { p ->
                        val cb = InventoryEventsListener.inventoryMap[p.uniqueId]
                        cb?.skipCloseCallback = true
                        p.closeInventory()

                        Schedulers.sync().runLater({
                            ViewKitContentsMenu(kit).openMenu(p)
                        }, 1L)
                    }
                )

                // Fill remaining bottom row slots with glass pane placeholders
                val placeholderItem = ItemBuilder
                    .of(XMaterial.GRAY_STAINED_GLASS_PANE)
                    .name(" ")
                    .build()

                for (slot in bottomRowSlots)
                {
                    if (slot !in actionButtons)
                    {
                        actionButtons[slot] = CallbackButton(
                            item = placeholderItem,
                            onClick = { }
                        )
                    }
                }

                // 45-slot menu: 36 editable item slots + 9 bottom action row
                BukkitInventoryCallback(
                    contentsToSet = currentLoadout.copyOf(45),
                    immutableSlots = bottomRowSlots,
                    title = "${kit.displayName} - Edit Loadout",
                    size = 45,
                    buttons = actionButtons,
                    callback = { contents ->
                        // Default close = save
                        val pr = HungerGamesProfileService.find(player) ?: return@BukkitInventoryCallback
                        val itemContents = Array<ItemStack?>(36) { i -> contents[i] }
                        pr.customLoadouts[kit.id] = itemContents
                        pr.save()

                        player.sendMessage(
                            "${CC.GREEN}Your custom loadout for ${CC.GOLD}${kit.displayName}${CC.GREEN} has been saved!"
                        )
                    }
                ).openMenu(player)
            }

        // Prestige button
        val kitStats = profile?.getStatsFor(kit.id)
        val kitKills = kitStats?.kills ?: 0L
        val prestigeLevel = profile?.getPrestige(kit.id) ?: 0
        val alreadyPrestiged = prestigeLevel >= 1
        val meetsPrestigeKills = kitKills >= HungerGamesProfile.PRESTIGE_KILL_REQUIREMENT
        val meetsPrestigeCoins = balance >= HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT
        val canPrestige = !alreadyPrestiged && meetsPrestigeKills && meetsPrestigeCoins
        val coinReward = kitKills * 10

        buttons[33] = ItemBuilder
            .of(
                when {
                    alreadyPrestiged -> XMaterial.YELLOW_STAINED_GLASS
                    canPrestige -> XMaterial.LIME_STAINED_GLASS
                    else -> XMaterial.RED_STAINED_GLASS
                }
            )
            .name(
                when {
                    alreadyPrestiged -> "${CC.GOLD}✦ Prestiged!"
                    canPrestige -> "${CC.GREEN}✦ Prestige Available!"
                    else -> "${CC.RED}✦ Prestige"
                }
            )
            .addToLore(
                "${CC.GRAY}Prestige resets your kit levels",
                "${CC.GRAY}but unlocks the exclusive",
                "${CC.GRAY}prestige loadout!",
                ""
            )
            .apply {
                if (alreadyPrestiged)
                {
                    addToLore(
                        "${CC.GOLD}You have already prestiged",
                        "${CC.GOLD}this kit!"
                    )
                } else
                {
                    addToLore(
                        "${CC.GRAY}Requirements:",
                        "${if (meetsPrestigeKills) CC.GREEN else CC.RED} ✦ ${CC.WHITE}${
                            Numbers.format(HungerGamesProfile.PRESTIGE_KILL_REQUIREMENT)
                        } Kit Kills ${CC.GRAY}(${Numbers.format(kitKills)}/${
                            Numbers.format(HungerGamesProfile.PRESTIGE_KILL_REQUIREMENT)
                        })",
                        "${if (meetsPrestigeCoins) CC.GREEN else CC.RED} ✦ ${CC.WHITE}${
                            Numbers.format(HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT)
                        } Arcade Coins ${CC.GRAY}(${Numbers.format(balance)}/${
                            Numbers.format(HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT)
                        })",
                        "",
                        "${CC.GRAY}Rewards:",
                        "${CC.GOLD} ✦ ${CC.WHITE}${Numbers.format(coinReward)} Arcade Coins",
                        "${CC.AQUA} ✦ ${CC.WHITE}${kit.displayName} Prestige Loadout",
                        "${CC.LIGHT_PURPLE} ✦ ${CC.WHITE}${kit.displayName} Kill Effect",
                        "",
                        if (canPrestige) "${CC.GREEN}Click to prestige!"
                        else "${CC.RED}You do not meet the requirements."
                    )
                }
            }
            .toButton { _, _ ->
                if (alreadyPrestiged)
                {
                    Button.playFail(player)
                    player.sendMessage("${CC.RED}You have already prestiged this kit!")
                    return@toButton
                }

                val prof = HungerGamesProfileService.find(player)
                    ?: return@toButton

                val currentKitKills = prof.getStatsFor(kit.id).kills
                if (currentKitKills < HungerGamesProfile.PRESTIGE_KILL_REQUIREMENT)
                {
                    Button.playFail(player)
                    player.sendMessage(
                        "${CC.RED}You need ${CC.GOLD}${Numbers.format(HungerGamesProfile.PRESTIGE_KILL_REQUIREMENT)} kit kills${CC.RED}! You have ${CC.GOLD}${Numbers.format(currentKitKills)}${CC.RED}."
                    )
                    return@toButton
                }

                val currentBalance = EconomyProfileService.find(player)
                    ?.balance(ECONOMY_ID) ?: 0L

                if (currentBalance < HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT)
                {
                    Button.playFail(player)
                    player.sendMessage(
                        "${CC.RED}You need ${CC.D_PURPLE}${Numbers.format(HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT)} Arcade Coins${CC.RED}! You have ${CC.D_PURPLE}${Numbers.format(currentBalance)}${CC.RED}."
                    )
                    return@toButton
                }

                // Charge the prestige cost
                TransactionService.submit(
                    Transaction(
                        sender = player.uniqueId,
                        receiver = Accounts.SERVER,
                        type = TransactionType.Purchase,
                        economy = ECONOMY_ID,
                        amount = HungerGamesProfile.PRESTIGE_COIN_REQUIREMENT
                    )
                ).thenAccept { result ->
                    if (result != TransactionResult.Success)
                    {
                        player.sendMessage("${CC.RED}Transaction failed! Please try again.")
                        return@thenAccept
                    }

                    val reward = prof.getStatsFor(kit.id).kills * 10

                    // Reset kit levels
                    prof.purchasedKits.remove(kit.id)
                    if (prof.selectedKit == kit.id)
                    {
                        prof.selectedKitLevel = 1
                    }

                    // Set prestige
                    prof.kitPrestiges[kit.id] = (prof.getPrestige(kit.id)) + 1
                    prof.save()

                    // Deposit coin reward
                    TransactionService.submit(
                        Transaction(
                            sender = Accounts.SERVER,
                            receiver = player.uniqueId,
                            type = TransactionType.Deposit,
                            economy = ECONOMY_ID,
                            amount = reward
                        )
                    )

                    Button.playNeutral(player)
                    player.sendMessage("")
                    player.sendMessage("${CC.GOLD}${CC.BOLD}✦ KIT PRESTIGED! ✦")
                    player.sendMessage("${CC.GRAY}You prestiged the ${CC.GOLD}${kit.displayName}${CC.GRAY} kit!")
                    player.sendMessage("")
                    player.sendMessage("${CC.GRAY}Rewards received:")
                    player.sendMessage("${CC.GOLD} ✦ ${CC.WHITE}${Numbers.format(reward)} Arcade Coins")
                    player.sendMessage("${CC.AQUA} ✦ ${CC.WHITE}${kit.displayName} Prestige Loadout Unlocked!")
                    player.sendMessage("${CC.LIGHT_PURPLE} ✦ ${CC.WHITE}${kit.displayName} Kill Effect")
                    player.sendMessage("")

                    // Refresh menu
                    Schedulers.sync().runLater({
                        ViewKitContentsMenu(kit).openMenu(player)
                    }, 1L)
                }
            }

        return buttons
    }

    override fun onClose(player: Player, manualClose: Boolean)
    {
        if (manualClose)
        {
            Schedulers
                .sync()
                .run {
                    MainHungerGamesKitMenu().openMenu(player)
                }
        }
    }

    private fun formatItemName(item: ItemStack): String
    {
        if (item.hasItemMeta() && item.itemMeta?.hasDisplayName() == true)
        {
            return "${CC.RESET}${item.itemMeta!!.displayName}"
        }

        return item.type.name
            .lowercase()
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }
}
