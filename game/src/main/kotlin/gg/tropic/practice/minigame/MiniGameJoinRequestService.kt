package gg.tropic.practice.minigame

import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.handler.MiniGameJoinIntoGameHandler
import gg.tropic.practice.minigame.handler.MiniGameSpectateHandler
import gg.tropic.practice.minigame.handler.RestartInstanceHandler

/**
 * @author GrowlyX
 * @since 10/20/2023
 */
@Service
object MiniGameJoinRequestService
{
    @Configure
    fun configure()
    {
        MiniGameRPC.spectateService.addHandler(MiniGameSpectateHandler())
        MiniGameRPC.joinIntoGameService.addHandler(MiniGameJoinIntoGameHandler())
        MiniGameRPC.restartInstanceService.addHandler(RestartInstanceHandler())
    }
}

