package mc.arch.minigames.parties.sync

import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Close
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import mc.arch.minigames.parties.PartiesPlugin
import mc.arch.minigames.parties.service.NetworkPartyService
import mc.arch.minigames.parties.toParty
import me.lucko.helper.Events
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.CC
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Class created on 12/20/2024

 * @author Max C.
 * @project scala-cgs
 * @website https://solo.to/redis
 */
@Service
object CrossServerPartyManager
{
    @Inject
    lateinit var plugin: PartiesPlugin

    @Configure
    fun configure()
    {
        Events
            .subscribe(PlayerJoinEvent::class.java)
            .handler { event ->
                val player = event.player
                val party = player.toParty()
                    ?: return@handler

                // Auto-warp is always on: whenever a party leader changes servers
                // the rest of the party is pulled to them, with no opt-in setting.
                if (party.leader.uniqueId == player.uniqueId && party.members.isNotEmpty())
                {
                    Schedulers
                        .async()
                        .run {
                            NetworkPartyService.warpPartyHere(party)
                            player.sendMessage("${CC.DARK_GRAY}You are now bringing all your party members to your current server.")
                        }
                }
            }
    }
}
