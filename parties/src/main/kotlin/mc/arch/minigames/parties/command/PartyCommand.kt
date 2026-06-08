package mc.arch.minigames.parties.command

import gg.scala.basics.plugin.disguise.DisguiseService
import gg.scala.basics.plugin.profile.BasicsProfile
import gg.scala.basics.plugin.settings.defaults.values.StateSettingValue
import gg.scala.commons.ScalaCommons
import gg.scala.commons.acf.CommandHelp
import gg.scala.commons.acf.ConditionFailedException
import gg.scala.commons.acf.annotation.*
import gg.scala.commons.acf.annotation.Optional
import gg.scala.commons.annotations.commands.AutoRegister
import gg.scala.commons.command.ScalaCommand
import gg.scala.commons.issuer.ScalaPlayer
import gg.scala.commons.playerstatus.canVirtuallySee
import gg.scala.flavor.inject.Inject
import gg.scala.lemon.player.wrapper.AsyncLemonPlayer
import gg.scala.lemon.util.QuickAccess
import gg.scala.lemon.util.QuickAccess.username
import gg.scala.store.controller.DataStoreObjectControllerCache
import gg.scala.store.storage.type.DataStoreStorageType
import mc.arch.minigames.parties.PartiesPlugin
import mc.arch.minigames.parties.menu.PartyManageMenu
import mc.arch.minigames.parties.model.*
import mc.arch.minigames.parties.service.NetworkPartyService
import mc.arch.minigames.parties.stream.PartyMessageStream
import mc.arch.minigames.parties.toDisplayName
import mc.arch.minigames.parties.toParty
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.bukkit.FancyMessage
import net.md_5.bungee.api.chat.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * @author GrowlyX
 * @since 12/17/2021
 */
@AutoRegister
@CommandAlias("party|p|parties")
object PartyCommand : ScalaCommand()
{
    @Default
    @CommandCompletion("@players")
    fun onDefault(
        player: ScalaPlayer,
        @Optional target: AsyncLemonPlayer?
    ): CompletableFuture<*>
    {
        if (target == null)
        {
            player.bukkit().performCommand("party help")
            return CompletableFuture.completedFuture(null)
        }

        return onInvite(player.bukkit(), target)
    }

    @HelpCommand
    fun onHelp(help: CommandHelp)
    {
        help.showHelp()
    }

    @Subcommand("chat")
    @Description("Send a chat message to your party!")
    fun onChat(player: ScalaPlayer, message: String) = PartyChatCommand.onPartyChat(player, message)

    @Subcommand("role")
    @Description("Set a user's role in your party.")
    fun onRole(
        player: Player,
        target: AsyncLemonPlayer,
        role: PartyRole
    ) = target.validatePlayers(player, false) {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        if (player.uniqueId != existing.leader.uniqueId)
        {
            throw ConditionFailedException("You must be the leader of your party to promote others.")
        }

        if (it.uniqueId == player.uniqueId)
        {
            throw ConditionFailedException("You're unable to modify your own party role.")
        }

        if (role == PartyRole.LEADER)
        {
            throw ConditionFailedException("You're unable to set another player's role to leader.")
        }

        val member = existing
            .findMember(it.uniqueId)
            ?: throw ConditionFailedException(
                "The player you specified is not a party member."
            )

        member.role = role

        val fancy = FancyMessage()
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
            .withMessage("${CC.GREEN}${it.uniqueId.toDisplayName()}'s ${CC.YELLOW}role has been set to ${CC.AQUA}${role.formatted}${CC.YELLOW}.\n")
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")

        NetworkPartyService.updateParty(existing)
        PartyMessageStream.pushToStream(existing, fancy)
    }

    @Subcommand("info|view|show|list")
    @Description("View party details!")
    fun onInfo(
        player: Player
    ): CompletableFuture<*>
    {
        return showPartyDetailsOf(player)
    }

    fun showPartyDetailsOf(player: Player) = CompletableFuture
        .runAsync {
            val party = NetworkPartyService.findParty(player.uniqueId)
                ?: throw ConditionFailedException(
                    "You're not in a party."
                )

            val displayName = party.leader.uniqueId.toDisplayName()
            player.sendMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
            player.sendMessage("${CC.B_YELLOW}$displayName${CC.YELLOW}'s Party:")
            player.sendMessage("${CC.YELLOW}Status: ${party.status.formatted}")
            player.sendMessage("")

            val settings = mutableListOf<String>()
            if (party.isEnabled(PartySetting.CHAT_MUTED))
            {
                settings += "${CC.RED}Chat Muted"
            }

            if (party.isEnabled(PartySetting.ALL_INVITE))
            {
                settings += "${CC.GREEN}All Invite"
            }

            player.sendMessage("${CC.YELLOW}Settings:${
                if (settings.isEmpty()) " ${CC.RED}None" else " ${settings.joinToString("${CC.GRAY}, ")}"
            }")
            player.sendMessage("")
            player.sendMessage("${CC.YELLOW}Members ${CC.GRAY}(${
                party.includedMembers().size
            })${CC.YELLOW}:")
            player.sendMessage(
                party.includedMembers().joinToString("${CC.GRAY}, ") {
                    it.toDisplayName()
                }
            )
            player.sendMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
        }

    @Subcommand("leave")
    @Description("Leave your current party!")
    fun onLeave(player: Player): CompletableFuture<Void>
    {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        if (player.uniqueId == existing.leader.uniqueId)
        {
            return onDisband(player)
        }

        existing.members.remove(player.uniqueId)
        return CompletableFuture.runAsync {
            NetworkPartyService.updateParty(existing)
            existing.sendMessage(FancyMessage()
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()}${CC.YELLOW} left the party.\n")
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
        }
    }

    @Subcommand("hijack")
    @CommandCompletion("@players")
    @Description("Hijacks a party from a user (Admins Only)")
    @CommandPermission("parties.command.hijack")
    fun onPartyHijack(
        player: Player, target: AsyncLemonPlayer
    ) = target.validatePlayers(player, false) {
        if (player.toParty() != null)
        {
            throw ConditionFailedException(
                "You are already in a party!"
            )
        }

        val party = NetworkPartyService.findParty(it.uniqueId)
            ?: throw ConditionFailedException("${CC.YELLOW}${it.name}${CC.RED} does not have a party!")

        party.members[player.uniqueId] = PartyMember(
            uniqueId = player.uniqueId,
            role = PartyRole.MEMBER
        )

        party.givePartyTo(player.uniqueId)
        NetworkPartyService.updateParty(party)

        party.sendMessage(FancyMessage()
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
            .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()}${CC.YELLOW} has hijacked the party!\n")
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
    }

    @Subcommand("join")
    @CommandCompletion("@players")
    @Description("Join a public and/or password protected party!")
    fun onJoin(
        player: Player, target: AsyncLemonPlayer,
        @Optional password: String?
    ) = target.validatePlayers(player, false) {
        if (player.toParty() != null)
        {
            throw ConditionFailedException(
                "You are already in a party!"
            )
        }

        val party = NetworkPartyService.findParty(it.uniqueId)
        if (party == null)
        {
            throw ConditionFailedException("${CC.YELLOW}${it.name}${CC.RED} does not have a party!")
        }

        if (party.includedMembers().size >= party.limit)
        {
            throw ConditionFailedException("You cannot join that party as its full!")
        }

        if (party.status != PartyStatus.PUBLIC)
        {
            if (party.status == PartyStatus.PROTECTED)
            {
                if (password == null)
                {
                    throw ConditionFailedException("You need to provide a password to join this party!")
                } else
                {
                    if (party.password != password)
                    {
                        throw ConditionFailedException("You provided the incorrect password for this party!")
                    }
                }
            } else
            {
                throw ConditionFailedException("You need an invitation to join this party!")
            }
        }

        party.members.put(player.uniqueId, PartyMember(
            uniqueId = player.uniqueId,
            role = PartyRole.MEMBER
        ))

        NetworkPartyService.updateParty(party)
        party.sendMessage(FancyMessage()
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
            .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()}${CC.YELLOW} joined the party.\n")
            .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
    }

    @Private
    @Subcommand("accept")
    fun onAccept(player: Player, @Name("partyId") rawUniqueId: String): CompletableFuture<Void>
    {
        val existing = player.toParty()
        if (existing != null)
        {
            throw ConditionFailedException("You're already in a party! Please use ${CC.BOLD}/party leave${CC.RED} to join a new one.")
        }

        val parsedUniqueId = kotlin.runCatching { UUID.fromString(rawUniqueId) }
            .getOrNull()
            ?: throw ConditionFailedException(
                "No party with the ID ${CC.YELLOW}$rawUniqueId${CC.YELLOW} exists."
            )

        return hasOutgoingInvite(parsedUniqueId, player.uniqueId)
            .thenAcceptAsync { hasInvite ->
                if (!hasInvite)
                {
                    throw ConditionFailedException("You do not have an invite from this party!")
                }

                val requestKey = "parties:invites:${player.uniqueId}:$parsedUniqueId"
                ScalaCommons.bundle().globals().redis().sync().hdel(
                    requestKey, parsedUniqueId.toString()
                )

                val party = NetworkPartyService.findPartyByID(parsedUniqueId)
                    ?: throw ConditionFailedException("The party you tried to join does not exist!")

                party.members.put(player.uniqueId, PartyMember(
                    uniqueId = player.uniqueId,
                    role = PartyRole.MEMBER
                ))

                NetworkPartyService.updateParty(party)
                party.sendMessage(FancyMessage()
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                    .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()}${CC.YELLOW} joined the party.\n")
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
            }
    }

    @Subcommand("invite")
    @CommandCompletion("@players")
    @Description("Invite a player to your party!")
    fun onInvite(player: Player, target: AsyncLemonPlayer) =
        target.validatePlayers(player, false) {
            if (DisguiseService.getApplicantProfileMapping(it.uniqueId).join() != null)
            {
                throw ConditionFailedException(
                    "No user entry matching the username ${CC.YELLOW}${it.name}${CC.RED} was found."
                )
            }

            val existing = player.toParty()
            if (it.uniqueId == player.uniqueId)
            {
                throw ConditionFailedException("You cannot invite yourself to your party!")
            }

            if (existing == null)
            {
                // we'll automatically create a party for them and then invite
                onCreate(player)
                    .thenRun {
                        // some recursive error stuff
                        player.performCommand("party invite ${it.uniqueId.toString()}")
                    }
                    .join()
                return@validatePlayers
            }

            if (!QuickAccess.online(player.uniqueId).join() || !player.canVirtuallySee(it.uniqueId))
            {
                throw ConditionFailedException(
                    "The player ${CC.YELLOW}${it.name}${CC.RED} is not online!"
                )
            }

            if (existing.includedMembers().size >= existing.limit)
            {
                throw ConditionFailedException("You cannot invite anymore people to your party as its full.")
            }

            val member = existing.findMember(player.uniqueId)!!
            val allInvite = existing.isEnabled(PartySetting.ALL_INVITE)

            if (!allInvite && !(member.role over PartyRole.MEMBER))
            {
                throw ConditionFailedException("You do not have permission to invite members! Your role is: ${member.role.formatted}")
            }

            if (existing.findMember(it.uniqueId) != null)
            {
                throw ConditionFailedException("${CC.YELLOW}${it.name}${CC.RED} is already in your party.")
            }

            val hasInvite = hasOutgoingInvite(existing.uniqueId, it.uniqueId)
                .join()

            if (hasInvite)
            {
                throw ConditionFailedException("A party invite was already sent out to this user.")
            }

            handlePostOutgoingInvite(player, it.uniqueId, existing).join()
        }

    @Subcommand("transfer|leader")
    @CommandCompletion("@players")
    @Description("Transfers your party to another user!")
    fun onTransfer(player: Player, target: AsyncLemonPlayer) =
        target.validatePlayers(player, false) {
            val existing = player.toParty()
                ?: throw ConditionFailedException("You're not in a party.")

            val selfMember = existing
                .findMember(player.uniqueId)!!

            if (selfMember.role != PartyRole.LEADER)
            {
                throw ConditionFailedException("You do not have permission to transfer this party! Your role is: ${selfMember.role.formatted}")
            }

            val targetMember = existing
                .findMember(it.uniqueId)
                ?: throw ConditionFailedException(
                    "${CC.YELLOW}${it.name}${CC.RED} is not in your party."
                )

            if (it.uniqueId == player.uniqueId)
            {
                throw ConditionFailedException("You cannot transfer this party to yourself!")
            }

            existing.givePartyTo(it.uniqueId)
            NetworkPartyService.updateParty(existing)

            existing.sendMessage(
                FancyMessage()
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                    .withMessage("${CC.GREEN}${it.uniqueId.toDisplayName()}${CC.YELLOW} was given ownership of the party.\n")
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
            )
        }

    @Subcommand("promote")
    @CommandCompletion("@players")
    @Description("Promotes a member of your party!")
    fun onPromote(player: Player, target: AsyncLemonPlayer) =
        target.validatePlayers(player, false) {
            val existing = player.toParty()
                ?: throw ConditionFailedException("You're not in a party.")

            val selfMember = existing
                .findMember(player.uniqueId)!!

            if (selfMember.role != PartyRole.LEADER)
            {
                throw ConditionFailedException("You do not have permission to promote this user! Your role is: ${selfMember.role.formatted}")
            }

            val targetMember = existing
                .findMember(it.uniqueId)
                ?: throw ConditionFailedException(
                    "${CC.YELLOW}${it.name}${CC.RED} is not in your party."
                )

            if (it.uniqueId == player.uniqueId)
            {
                throw ConditionFailedException("You cannot promote yourself!")
            }

            if (targetMember.role == PartyRole.MODERATOR || targetMember.role == PartyRole.LEADER)
            {
                throw ConditionFailedException(
                    "You cannot promote a player past moderator! If you want to give them the party, do ${CC.YELLOW}/party transfer${CC.RED}."
                )
            }

            val newRole = existing.escalateMemberRole(it.uniqueId)
            NetworkPartyService.updateParty(existing)

            existing.sendMessage(
                FancyMessage()
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                    .withMessage("${CC.GREEN}${it.uniqueId.toDisplayName()}${CC.YELLOW} has been promoted to ${newRole.formatted}.\n")
                    .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
            )
        }

    @Subcommand("kick|remove")
    @CommandCompletion("@players")
    @Description("Kick a player from your party!")
    fun onKick(player: Player, target: AsyncLemonPlayer) =
        target.validatePlayers(player, false) {
            val existing = player.toParty()
                ?: throw ConditionFailedException("You're not in a party.")

            val selfMember = existing
                .findMember(player.uniqueId)!!

            if (!(selfMember.role over PartyRole.MODERATOR))
            {
                throw ConditionFailedException("You do not have permission to kick members! Your role is: ${selfMember.role.formatted}")
            }

            val targetMember = existing
                .findMember(it.uniqueId)
                ?: throw ConditionFailedException(
                    "${CC.YELLOW}${it.name}${CC.RED} is not in your party."
                )

            if (it.uniqueId == existing.leader.uniqueId)
            {
                throw ConditionFailedException("You do not have permission to kick the party leader!")
            }

            if (it.uniqueId == player.uniqueId)
            {
                throw ConditionFailedException("You cannot kick yourself from your party!")
            }

            existing.members.remove(targetMember.uniqueId)

            NetworkPartyService.updateParty(existing)
            existing.sendMessage(FancyMessage()
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                .withMessage("${CC.GREEN}${it.uniqueId.toDisplayName()}${CC.YELLOW} was kicked from the party.\n")
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
        }

    private fun handlePostOutgoingInvite(
        player: Player, target: UUID, party: Party
    ): CompletableFuture<Void>
    {
        if (Bukkit.getPluginManager().isPluginEnabled("ScBasics"))
        {
            return DataStoreObjectControllerCache
                .findNotNull<BasicsProfile>()
                .load(target, DataStoreStorageType.MONGO)
                .thenCompose {
                    if (it == null)
                    {
                        throw ConditionFailedException("${CC.YELLOW}${target.username()}${CC.RED} has never logged on the server.")
                    }

                    val stateSettingValue = it
                        .setting(
                            id = "party_invites",
                            default = StateSettingValue.ENABLED
                        )

                    if (stateSettingValue == StateSettingValue.DISABLED)
                    {
                        throw ConditionFailedException(
                            "${CC.YELLOW}${target.username()}${CC.RED} has their party invites disabled."
                        )
                    }

                    internalHandlePartyInviteDispatch(player, target, party)
                }
        }

        return AsyncLemonPlayer.of(target)
            .computeNow()
            .thenCompose {
                if (it.isEmpty())
                {
                    throw ConditionFailedException("${CC.YELLOW}${target.username()}${CC.RED} has never logged on the server.")
                }

                val disabled = it[0]
                    .getSetting("party-invites-disabled")

                if (disabled)
                {
                    throw ConditionFailedException("${CC.YELLOW}${target.username()}${CC.RED} has their party invites disabled.")
                }

                internalHandlePartyInviteDispatch(player, target, party)
            }
    }

    private fun internalHandlePartyInviteDispatch(
        player: Player, target: UUID, party: Party
    ): CompletableFuture<Void>
    {
        return CompletableFuture.runAsync {
            val message = FancyMessage()
            message.withMessage(
                "${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}",
                "${CC.GREEN}${player.uniqueId.toDisplayName()}${CC.YELLOW} sent you a party invite!",
                "${CC.AQUA}[Click to accept]",
                "${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}",
            )
            message.andHoverOf(
                "${CC.GREEN}Click to accept ${CC.YELLOW}${player.name}'s${CC.GREEN} party invite!"
            )
            message.andCommandOf(
                ClickEvent.Action.RUN_COMMAND,
                "/party accept ${party.uniqueId}"
            )

            val requestKey = "parties:invites:$target:${party.uniqueId}"
            ScalaCommons.bundle().globals().redis().sync().hset(
                requestKey,
                party.uniqueId.toString(),
                System.currentTimeMillis().toString()
            )

            ScalaCommons.bundle().globals().redis().sync().expire(
                requestKey,
                TimeUnit.MINUTES.toSeconds(5L)
            )

            QuickAccess.sendGlobalPlayerFancyMessage(
                fancyMessage = message, uuid = target
            )

            party.sendMessage(FancyMessage()
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                .withMessage("${CC.GREEN}${target.toDisplayName()}${CC.YELLOW} was invited to the party!\n")
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
            )
        }
    }

    fun hasOutgoingInvite(party: UUID, target: UUID): CompletableFuture<Boolean>
    {
        return CompletableFuture.supplyAsync {
            var exists: Boolean

            ScalaCommons.bundle().globals().redis().sync().apply {
                exists = hget("parties:invites:$target:$party", party.toString()) != null
            }

            return@supplyAsync exists
        }
    }

    @Subcommand("manage")
    @Description("Manage internal settings of your party!")
    fun onManage(player: Player)
    {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        val member = existing
            .findMember(player.uniqueId)!!

        if (!(member.role over PartyRole.MODERATOR))
        {
            throw ConditionFailedException("You do not have permission to access the party management menu! Your role is: ${member.role.formatted}")
        }

        PartyManageMenu(existing, member.role).openMenu(player)
    }

    @Subcommand("warp")
    @Description("Warp players to your server!")
    fun onWarp(player: Player)
    {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        val member = existing
            .findMember(player.uniqueId)!!

        if (!(member.role over PartyRole.MODERATOR))
        {
            throw ConditionFailedException("You do not have permission to warp! Your role is: ${member.role.formatted}")
        }

        CompletableFuture.runAsync {
            NetworkPartyService.warpPartyHere(existing)
        }
    }

    @Subcommand("disband")
    @Description("Disband your party!")
    fun onDisband(player: Player): CompletableFuture<Void>
    {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        if (existing.leader.uniqueId != player.uniqueId)
        {
            throw ConditionFailedException(
                "You cannot disband the party!"
            )
        }

        return CompletableFuture.runAsync {
            existing.sendMessage(FancyMessage()
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                .withMessage("${CC.RED}Your party has been disbanded.\n")
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))

            NetworkPartyService.delete(existing)
        }
    }

    @Subcommand("create")
    @Description("Create a new party!")
    fun onCreate(player: Player): CompletableFuture<Void>
    {
        val existing = player.toParty()
        if (existing != null)
        {
            throw ConditionFailedException("You're already in a party.")
        }

        return CompletableFuture.runAsync {
            NetworkPartyService.createParty(player.uniqueId)

            player.sendMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
            player.sendMessage("${CC.GREEN}You have created a new party!")
            player.sendMessage("${CC.YELLOW}Use ${CC.AQUA}/party help${CC.YELLOW} to view all party-related commands!")
            player.sendMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}")
        }
    }

    @Subcommand("private")
    @Description("Toggle Private Games mode for your party!")
    fun onPrivate(player: Player): CompletableFuture<Void>
    {
        val existing = player.toParty()
            ?: throw ConditionFailedException("You're not in a party.")

        if (existing.leader.uniqueId != player.uniqueId)
        {
            throw ConditionFailedException("Only the party leader can toggle Private Games mode!")
        }

        val wasEnabled = existing.isEnabled(PartySetting.PRIVATE_GAMES)
        existing.update(PartySetting.PRIVATE_GAMES, !wasEnabled)

        return existing.saveAndUpdateParty().thenRun {
            val statusText = if (!wasEnabled) 
                "${CC.GREEN}enabled" 
            else 
                "${CC.RED}disabled"
            
            existing.sendMessage(FancyMessage()
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}\n")
                .withMessage("${CC.GREEN}${player.uniqueId.toDisplayName()} ${CC.YELLOW}has $statusText ${CC.LIGHT_PURPLE}Private Games${CC.YELLOW} mode!\n")
                .withMessage("${CC.GRAY}${CC.STRIKE_THROUGH}${" ".repeat(53)}"))
        }
    }
}
