package gg.tropic.practice.replacements

import gg.scala.commons.agnostic.sync.server.ServerContainer
import gg.scala.commons.agnostic.sync.server.impl.GameServer
import gg.tropic.practice.messaging.ExternalPlayerCountApiSubmissionService
import gg.tropic.practice.games.livePlayerCount
import gg.tropic.practice.metadata.SystemMetadataService
import net.evilblock.cubed.util.math.Numbers

/**
 * @author Subham
 * @since 7/6/25
 */
fun String.toTemplatePlayerCounts() = template { key, data ->
    when (key)
    {
        "group" ->
        {
            Numbers.format(
                ServerContainer
                    .getServersInGroupCasted<GameServer>(data)
                    .sumOf { it.getPlayersCount() ?: 0 }
            )
        }

        "legacyplayercount" ->
        {
            Numbers.format(
                ExternalPlayerCountApiSubmissionService.getLegacyPlayerCount(data)
            )
        }

        "minigame" ->
        {
            val (group, minigameID) = data.split("_")
            Numbers.format(
                ServerContainer
                    .getServersInGroupCasted<GameServer>(group)
                    .sumOf { it.getPlayersCount() ?: 0 } +
                    SystemMetadataService.allGames()
                        .filter { (minigameID == "duels" && it.miniGameType == null) || it.miniGameType == minigameID }
                        .sumOf { it.livePlayerCount() }
            )
        }

        "arcade" ->
        {
            val arcadeTypes = setOf("arcade", "skywars", "miniwalls", "hungergames", "pof")
            Numbers.format(
                ServerContainer
                    .getServersInGroupCasted<GameServer>(data)
                    .sumOf { it.getPlayersCount() ?: 0 } +
                    SystemMetadataService.allGames()
                        .filter { it.miniGameType in arcadeTypes }
                        .sumOf { it.livePlayerCount() }
            )
        }

        "hwi" ->
        {
            Numbers.format(
                SystemMetadataService.allHostedWorldInstances()
                    .filter { it.type.name == data }
                    .sumOf { it.onlinePlayers.size }
            )
        }

        else -> ""
    }
}
