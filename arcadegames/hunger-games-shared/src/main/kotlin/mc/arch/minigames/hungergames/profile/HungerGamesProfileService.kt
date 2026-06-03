package mc.arch.minigames.hungergames.profile

import gg.scala.commons.persist.ProfileOrchestrator
import gg.scala.flavor.service.Service
import java.util.*

/**
 * @author ArchMC
 */
@Service
object HungerGamesProfileService : ProfileOrchestrator<HungerGamesProfile>()
{
    init
    {
        enableReadOnlyProfile()
    }

    override fun new(uniqueId: UUID) = HungerGamesProfile(uniqueId)
    override fun type() = HungerGamesProfile::class
}
