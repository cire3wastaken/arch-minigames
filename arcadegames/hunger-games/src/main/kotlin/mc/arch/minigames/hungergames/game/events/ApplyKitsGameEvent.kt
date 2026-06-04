package mc.arch.minigames.hungergames.game.events

import gg.tropic.practice.minigame.MiniGameEvent
import mc.arch.minigames.hungergames.game.HungerGamesLifecycle
import mc.arch.minigames.hungergames.kits.HungerGamesKitDataSync
import mc.arch.minigames.hungergames.profile.HungerGamesProfile
import mc.arch.minigames.hungergames.profile.HungerGamesProfileService
import net.evilblock.cubed.util.CC
import java.time.Duration

/**
 * @author ArchMC
 */
class ApplyKitsGameEvent(
    private val lifecycle: HungerGamesLifecycle,
    override val description: String = "Kits",
    override val duration: Duration = Duration.ofSeconds(60)
) : MiniGameEvent
{
    override fun execute()
    {
        val kitContainer = HungerGamesKitDataSync.cached()

        for (player in lifecycle.game.allNonSpectators())
        {
            val profile = HungerGamesProfileService.find(player) ?: continue

            var kitId = profile.selectedKit
            var kitLevel = profile.selectedKitLevel

            // Auto-assign the first default kit if none selected
            if (kitId == null || kitContainer.kits[kitId] == null)
            {
                val fallbackKit = kitContainer.kits.values
                    .firstOrNull { HungerGamesProfile.killRequirement(it.id) <= 0L }

                if (fallbackKit == null) continue

                kitId = fallbackKit.id
                kitLevel = 1
                profile.selectedKit = kitId
                profile.selectedKitLevel = kitLevel
                profile.save()

                player.sendMessage(
                    "${CC.RED}You did not select a kit in time, so you have been given ${CC.YELLOW}${fallbackKit.displayName} Kit${CC.RED}."
                )
            }

            val kit = kitContainer.kits[kitId] ?: continue

            kit.applyTo(player, kitLevel)
            player.sendMessage("${CC.GREEN}Your ${CC.GOLD}${kit.displayName}${CC.GREEN} kit has been applied!")
        }

        lifecycle.game.sendMessage("${CC.YELLOW}Kits have been distributed!")
    }
}
