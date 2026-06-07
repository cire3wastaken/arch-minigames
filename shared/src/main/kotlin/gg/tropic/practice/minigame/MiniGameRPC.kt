package gg.tropic.practice.minigame

import gg.tropic.practice.games.matchmaking.JoinIntoGameRequest
import gg.tropic.practice.games.matchmaking.JoinIntoGameResult
import gg.tropic.practice.games.restart.RestartInstanceRequest
import gg.tropic.practice.games.restart.RestartInstanceResponse
import gg.tropic.practice.games.spectate.SpectateRequest
import gg.tropic.practice.games.spectate.SpectateResponse
import mc.arch.commons.communications.rpc.CommunicationGateway
import mc.arch.commons.communications.rpc.createRPCService

/**
 * @author Subham
 * @since 7/20/25
 */
object MiniGameRPC
{
    private val gateway = CommunicationGateway("minigames")
    val spectateService = gateway.createRPCService<SpectateRequest, SpectateResponse>(
        serviceName = "spectate",
        timeoutSeconds = 3L
    )

    val joinIntoGameService = gateway.createRPCService<JoinIntoGameRequest, JoinIntoGameResult>(
        serviceName = "join-into-game",
        timeoutSeconds = 8L
    )

    val restartInstanceService = gateway.createRPCService<RestartInstanceRequest, RestartInstanceResponse>(
        serviceName = "restart-instance",
        timeoutSeconds = 5L
    )
}

