package gg.tropic.practice.subscription

import gg.scala.basics.plugin.profile.BasicsProfileService
import gg.scala.basics.plugin.settings.defaults.values.StateSettingValue
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.GameReference
import gg.tropic.practice.games.GameService
import gg.tropic.practice.games.GameService.gameMappings
import gg.tropic.practice.games.GameService.isShallHostHostedWorlds
import gg.tropic.practice.kit.feature.FeatureFlag
import gg.tropic.practice.metadata.InstanceMetadata
import gg.tropic.practice.minigame.MiniGameRegistry
import gg.tropic.practice.provider.MiniProviderType
import gg.tropic.practice.provider.MiniProviderVersion
import gg.tropic.practice.metadata.SystemMetadataService
import gg.tropic.practice.settings.DuelsSettingCategory
import gg.tropic.practice.ugc.HostedWorldInstanceService
import gg.tropic.practice.ugc.creator.HostedWorldInstanceCreatorRegistry
import net.evilblock.cubed.util.ServerVersion
import org.bukkit.entity.Player

/**
 * @author Subham
 * @since 7/18/25
 */
@Service
object StatusServiceSubscriptionService
{
    @Configure
    fun configure()
    {
        SystemMetadataService.bindToStatusService {
            InstanceMetadata(
                availableMinigames = MiniGameRegistry
                    .miniGameOrchestrators.keys.toSet(),
                version = if (ServerVersion.getVersion().isNewerThan(ServerVersion.v1_9))
                    MiniProviderVersion.MODERN else MiniProviderVersion.LEGACY,
                supportedTypes = if (isShallHostHostedWorlds)
                {
                    setOf(MiniProviderType.UGC, MiniProviderType.MINIGAME)
                } else
                {
                    setOf(MiniProviderType.MINIGAME)
                },
                supportedHostedWorldProviderTypes = HostedWorldInstanceCreatorRegistry
                    .availableProviders()
                    .toSet(),
                hostedWorldInstanceReferences = HostedWorldInstanceService
                    .worldInstances()
                    .map { it.reference() },
                games = gameMappings.toMap().values
                    .map {
                        val players = it.toBukkitPlayers()
                            .filterNotNull()
                            .toList()

                        val majoritySpectatorsEnabled = players
                            .mapNotNull(BasicsProfileService::find)
                            .count { profile ->
                                profile.setting(
                                    "${DuelsSettingCategory.DUEL_SETTING_PREFIX}:allow-spectators",
                                    StateSettingValue.ENABLED
                                ) == StateSettingValue.ENABLED
                            }

                        GameReference(
                            uniqueId = it.identifier,
                            mapID = it.map.name,
                            state = it.state,
                            players = it.toPlayers().toSet(),
                            onlinePlayers = it.toBukkitPlayers().filterNotNull().size,
                            onlinePlayerIds = players.map(Player::getUniqueId).toSet(),
                            spectators = it.arenaWorld.players
                                .toList()
                                .filter { player ->
                                    GameService.isSpectating(player)
                                }
                                .map(Player::getUniqueId)
                                .toSet(),
                            kitID = it.kit.id,
                            replicationID = it.arenaWorld.name,
                            server = ServerSync.local.id,
                            queueId = it.expectationModel.queueId,
                            majorityAllowsSpectators = players.isEmpty() ||
                                (majoritySpectatorsEnabled / players.size) >= 0.50,
                            metadata = it.expectationModel.matchmakingMetadataAPIV2,
                            miniGameType = it.flagMetaData(FeatureFlag.MiniGameType, "id"),
                            isPrivateGame = it.expectationModel.isPrivateGame
                        )
                    }
                    .toList()
            )
        }
    }
}
