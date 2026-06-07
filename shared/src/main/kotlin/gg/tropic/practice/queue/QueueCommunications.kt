package gg.tropic.practice.queue

import gg.scala.aware.thread.AwareThreadContext
import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.tropic.practice.communications.PracticePlayerCommsService.createMessage
import gg.tropic.practice.kit.Kit
import gg.tropic.practice.minigame.MiniGameQueueConfiguration
import gg.tropic.practice.profile.PracticeProfileService
import gg.tropic.practice.region.PlayerRegionFromRedisProxy
import gg.tropic.practice.region.Region
import gg.tropic.practice.settings.DuelsSettingCategory
import gg.tropic.practice.settings.restriction.RangeRestriction
import gg.tropic.practice.statistics.TrackedKitStatistic
import gg.tropic.practice.statistics.statisticIdFrom
import mc.arch.minigames.parties.service.NetworkPartyService
import me.lucko.helper.Helper
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.nms.MinecraftReflection
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * @author Subham
 * @since 8/3/25
 */
object QueueCommunications
{
    fun leaveQueue(playerId: UUID)
    {
        createMessage(
            packet = "leave",
            "leader" to playerId,
        ).publish(
            context = AwareThreadContext.ASYNC,
            channel = "communications-gamequeue"
        )
    }

    fun joinQueue(kit: Kit, queueType: QueueType, teamSize: Int, player: Player,
                  miniGameQueueConfiguration: MiniGameQueueConfiguration? = null): CompletableFuture<*>
    {
        val profile = PracticeProfileService.find(player)
            ?: return CompletableFuture.completedFuture(null)

        var players = listOf(player.uniqueId)
        val party = NetworkPartyService.findParty(player.uniqueId)
        if (party != null)
        {
            players = party.includedMembersOnline()
            if (party.leader.uniqueId != player.uniqueId)
            {
                player.sendMessage("${CC.RED}You must be the party leader to join a game!")
                return CompletableFuture.completedFuture(null)
            }

            if (players.size > 1 && teamSize == 1 && !player.hasPermission("minigame.admin"))
            {
                player.sendMessage("${CC.RED}The minigame you are trying to join is a Solo mode. You may not join with ${
                    players.size
                } players in your party.")
                return CompletableFuture.completedFuture(null)
            }

            if (players.size != teamSize && !player.hasMetadata("understands-team"))
            {
                player.sendMessage("${CC.RED}This mode has teams of $teamSize player${
                    if (teamSize == 1) "" else "s"
                }.")
                player.sendMessage("${CC.GRAY}If you join a game, you will be paired with random teammates.")
                player.sendMessage("${CC.B_WHITE}[Join a game again to confirm!]")
                player.setMetadata(
                    "understands-team",
                    FixedMetadataValue(Helper.hostPlugin(), true)
                )
                return CompletableFuture.completedFuture(null)
            }
        }

        return PlayerRegionFromRedisProxy.of(player)
            .exceptionally { Region.NA }
            .thenAcceptAsync {
                val statistic = profile
                    .getStatisticValue(
                        statisticIdFrom(TrackedKitStatistic.ELO) {
                            kit(kit)
                            queueType(queueType)
                        }
                    )
                    ?.score
                    ?: 1000

                createMessage(
                    packet = "join",
                    "entry" to QueueEntry(
                        leader = player.uniqueId,
                        leaderPing = MinecraftReflection.getPing(player),
                        queueRegion = it,
                        leaderELO = statistic.toInt(),
                        maxPingDiff = player.pingRange.sanitizedDiffsBy(),
                        players = players,
                        miniGameQueueConfiguration = miniGameQueueConfiguration
                    ),
                    "kit" to kit.id,
                    "queueType" to queueType,
                    "teamSize" to teamSize
                ).publish(
                    context = AwareThreadContext.SYNC,
                    channel = "communications-gamequeue"
                )
            }
            .exceptionally {
                player.sendMessage("${CC.RED}We were unable to put you in the queue!")
                return@exceptionally null
            }
    }

    val Player.pingRange: RangeRestriction
        get() = BasicsProfileService.find(this)
            ?.setting<RangeRestriction>(
                "${DuelsSettingCategory.DUEL_SETTING_PREFIX}:restriction-ping",
                RangeRestriction.None
            )
            ?: RangeRestriction.None
}
