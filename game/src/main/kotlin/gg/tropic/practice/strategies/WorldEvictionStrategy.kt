package gg.tropic.practice.strategies

import gg.scala.lemon.redirection.impl.VelocityRedirectSystem
import gg.tropic.practice.schematics.manipulation.BlockChanger
import gg.tropic.practice.map.MapReplicationService
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.bukkit.Tasks
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player

/**
 * @author Subham
 * @since 7/20/25
 */
object WorldEvictionStrategy
{
    fun evictWorld(world: World,
                   redirectTarget: String? = null,
                   kickPlayers: Boolean = true,
                   unloadWorld: Boolean = true,
                   generateRedirectMetadataFor: (Player) -> Map<String, String> = { mapOf() }
    )
    {
        if (kickPlayers)
        {
            val onlinePlayers = world.players

            if (onlinePlayers.isNotEmpty())
            {
                onlinePlayers.forEach {
                    redirectTarget?.apply {
                        VelocityRedirectSystem.redirect(
                            it, this,
                            generateRedirectMetadataFor(it)
                        )
                    }
                }
            }
        }

        Schedulers
            .async()
            .runLater({
                MapReplicationService.removeReplicationMatchingWorld(world)

                if (kickPlayers)
                {
                    val onlinePlayers = world.players
                    if (onlinePlayers.isNotEmpty())
                    {
                        Schedulers
                            .sync()
                            .run {
                                onlinePlayers.toList().forEach {
                                    runCatching {
                                        it.kickPlayer("You should not be on this server!")
                                    }
                                }
                            }
                            .join()

                        BlockChanger.invalidate(world)

                        Schedulers
                            .sync()
                            .runLater({
                                if (unloadWorld)
                                {
                                    Bukkit.unloadWorld(world, false)
                                }
                            }, 20L)
                            .join()
                        return@runLater
                    }
                }

                BlockChanger.invalidate(world)

                Schedulers
                    .sync()
                    .run {
                        if (unloadWorld)
                        {
                            Bukkit.unloadWorld(world, false)
                        }
                    }
                    .join()
            }, 60L)
    }
}
