package gg.tropic.practice.queue

import gg.tropic.practice.application.api.defaults.kit.ImmutableKit
import gg.tropic.practice.application.api.defaults.map.MapDataSync
import gg.tropic.practice.expectation.GameExpectation
import gg.tropic.practice.games.team.GameTeam
import gg.tropic.practice.games.team.TeamIdentifier
import gg.tropic.practice.persistence.RedisShared
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.region.Region
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.UUID

/**
 * @author Subham
 * @since 6/15/25
 */
class SubscribableDuelPlayerQueue(
    private val kit: ImmutableKit,
    private val teamSize: Int,
    private val queueType: QueueType,
    private val providerVersion: MiniProviderVersion = MiniProviderVersion.LEGACY,
    override val id: String = queueId {
        kit(kit.id)
        queueType(queueType)
        teamSize(teamSize)
    },
    override val internalQueue: InternalQueue<QueueEntry> = InternalQueue()
) : SubscribablePlayerQueue
{
    override fun onProcess(): List<QueueEntry>
    {
        val length = internalQueue.size()
        if (length < teamSize * 2)
        {
            return listOf()
        }

        val matchmakingStart = System.currentTimeMillis()

        // don't unnecessarily load in and map to data class if not needed
        val first: List<QueueEntry>
        val second: List<QueueEntry>

        // Faster than doing a list intersect which compares all items
        fun IntRange.quickIntersect(other: IntRange): Boolean
        {
            return this.first <= other.last && this.last >= other.first
        }

        val queueEntries = playersInQueue()
        val groupedQueueEntries = if (queueType == QueueType.Ranked)
        {
            queueEntries
                .map { entry ->
                    val otherEntriesMatchingEntry = queueEntries
                        .filter { otherEntry ->
                            val doesELOIntersect = entry.data.leaderRangedELO.toIntRangeInclusive()
                                .quickIntersect(
                                    otherEntry.data.leaderRangedELO
                                        .toIntRangeInclusive()
                                )

                            // we can ignore ping intersections if they have no ping restriction
                            val doesPingIntersect =
                                (entry.data.maxPingDiff == -1 || otherEntry.data.maxPingDiff == -1) ||
                                    entry.data.leaderRangedPing.toIntRangeInclusive()
                                        .quickIntersect(
                                            otherEntry.data.leaderRangedPing
                                                .toIntRangeInclusive()
                                        )

                            entry != otherEntry &&
                                doesELOIntersect &&
                                doesPingIntersect &&
                                ((entry.data.queueRegion == otherEntry.data.queueRegion) ||
                                    (entry.data.preferredQueueRegion == otherEntry.data.preferredQueueRegion))
                        }

                    otherEntriesMatchingEntry + entry
                }
                .filter {
                    it.size >= teamSize * 2
                }
        } else
        {
            queueEntries
                .map { entry ->
                    val otherEntriesMatchingEntry = queueEntries
                        .filter { otherEntry ->
                            // we can ignore ping intersections if they have no ping restriction
                            val doesPingIntersect =
                                (entry.data.maxPingDiff == -1 || otherEntry.data.maxPingDiff == -1) ||
                                    entry.data.leaderRangedPing.toIntRangeInclusive()
                                        .quickIntersect(
                                            otherEntry.data.leaderRangedPing
                                                .toIntRangeInclusive()
                                        )

                            entry != otherEntry &&
                                ((entry.data.queueRegion == otherEntry.data.queueRegion) ||
                                    (entry.data.preferredQueueRegion == otherEntry.data.preferredQueueRegion)) &&
                                doesPingIntersect
                        }

                    otherEntriesMatchingEntry + entry
                }
                .filter {
                    it.size >= teamSize * 2
                }
        }

        if (groupedQueueEntries.isEmpty())
        {
            return listOf()
        }

        val selectedQueueMatch = groupedQueueEntries.random()
            .map { it.data }

        // Expecting a symmetric list here, lets hope it doesn't break?
        first = selectedQueueMatch.take(teamSize)
        second = selectedQueueMatch.takeLast(teamSize)

        if (first.size != teamSize || second.size != teamSize)
        {
            return listOf()
        }

        val firstPlayers = first.flatMap { it.players }
        val secondPlayers = second.flatMap { it.players }
        val users = listOf(firstPlayers, secondPlayers).flatten()

        val map = MapDataSync
            .selectRandomMapCompatibleWith(kit, providerVersion)
            ?: return run {
                RedisShared.sendMessage(
                    users,
                    listOf(
                        "&c&lWe found no map compatible with the kit you are queueing for!"
                    )
                )
                selectedQueueMatch
            }

        val expectation = GameExpectation(
            identifier = UUID.randomUUID(),
            players = listOf(first, second)
                .flatMap {
                    it.flatMap { entry ->
                        entry.players
                    }
                }
                .toMutableSet(),
            teams = setOf(
                GameTeam(teamIdentifier = TeamIdentifier.A, players = firstPlayers.toMutableSet()),
                GameTeam(teamIdentifier = TeamIdentifier.B, players = secondPlayers.toMutableSet())
            ),
            kitId = kit.id,
            mapId = map.name,
            queueType = queueType,
            queueId = id
        )

        val region = first.first().preferredQueueRegion

        // Log match found event with timing and details
        val matchmakingDuration = System.currentTimeMillis() - matchmakingStart
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "queue.match_found"
            message = "Match found: ${expectation.players.size} players in ${kit.displayName} ($queueType)"
            level = SentryLevel.INFO
            setData("kit_id", kit.id)
            setData("queue_type", queueType.name)
            setData("team_size", teamSize)
            setData("map", map.name)
            setData("player_count", expectation.players.size)
            setData("region", region.name)
            setData("matchmaking_duration_ms", matchmakingDuration)
            setData("game_id", expectation.identifier.toString())
        })

        GameQueueManager
            .prepareGameFor(
                map = map,
                expectation = expectation,
                // prefer NA servers if queuing globally
                region = if (region == Region.Both) Region.NA else region,
                version = providerVersion
            )
            .exceptionally {
                Sentry.captureException(it) { scope ->
                    scope.setExtra("game_id", expectation.identifier.toString())
                    scope.setExtra("kit", kit.id)
                    scope.setExtra("player_count", expectation.players.size.toString())
                }
                it.printStackTrace()
                return@exceptionally null
            }

        return selectedQueueMatch
    }
}
