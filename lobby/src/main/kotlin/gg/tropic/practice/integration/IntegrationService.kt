package gg.tropic.practice.integration

import gg.scala.commons.spatial.Position
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.game.extensions.gems.rankgifting.RankGiftingPerkService
import gg.tropic.practice.configuration.PracticeConfigurationService
import me.lucko.helper.Schedulers
import org.bukkit.Bukkit

/**
 * Class created on 2/21/2026

 * @author Max C.
 * @project arch-minigames
 * @website https://solo.to/redis
 */
@Service
object IntegrationService
{
    @Configure
    fun configure()
    {
        val config = PracticeConfigurationService.local()
        val location = config.rankGiftLeaderboardLocation

        if (location != null)
        {
            val world = Bukkit.getWorlds().first()
            val hologram = RankGiftLeaderboardHologram(location.toLocation(world))

            hologram.configure()
        }

        val top3NPCs = config.rankGiftTop3NPCs
            ?: mutableListOf()
        val one = top3NPCs.getOrNull(0)
        val two = top3NPCs.getOrNull(1)
        val three = top3NPCs.getOrNull(2)

        one?.let {
            RankGiftPositionalNPC(
                1, it
            ).configure()
        }
        two?.let {
            RankGiftPositionalNPC(
                2, it
            ).configure()
        }
        three?.let {
            RankGiftPositionalNPC(
                3, it
            ).configure()
        }
    }
}
