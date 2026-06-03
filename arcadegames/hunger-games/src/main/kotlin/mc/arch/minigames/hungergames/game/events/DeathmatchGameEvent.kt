package mc.arch.minigames.hungergames.game.events

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.extensions.resetAttributes
import gg.tropic.practice.minigame.MiniGameEvent
import mc.arch.minigames.hungergames.game.HungerGamesLifecycle
import mc.arch.minigames.hungergames.lootpool.HGLootGenerator
import mc.arch.minigames.hungergames.lootpool.HGLootType
import me.lucko.helper.Schedulers
import net.evilblock.cubed.util.CC
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.block.Chest
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration

/**
 * @author ArchMC
 */
class DeathmatchGameEvent(
    private val lifecycle: HungerGamesLifecycle,
    override val description: String = "Deathmatch",
    override val duration: Duration = Duration.ofSeconds(90)
) : MiniGameEvent
{
    override fun execute()
    {
        lifecycle.deathmatch = true
        lifecycle.deathmatchStartedAt = System.currentTimeMillis()
        lifecycle.deathmatchGracePeriod = 10

        val world = lifecycle.game.arenaWorld
        val spawn = world.spawnLocation

        lifecycle.game.sendMessage("")
        lifecycle.game.sendMessage("${CC.RED}${CC.BOLD}DEATHMATCH!")
        lifecycle.game.sendMessage("${CC.GRAY}All players have been teleported to the center!")
        lifecycle.game.sendMessage("")

        val players = lifecycle.game.allNonSpectators()
        val radius = 15.0
        val angleStep = (2 * Math.PI) / players.size.coerceAtLeast(1)

        players.forEachIndexed { index, player ->
            val angle = angleStep * index
            val x = spawn.x + radius * Math.cos(angle)
            val z = spawn.z + radius * Math.sin(angle)
            val loc = Location(world, x, spawn.y + 1, z)
            loc.yaw = ((Math.toDegrees(Math.atan2(spawn.z - z, spawn.x - x)) - 90) % 360).toFloat()

            // Find safe Y
            val block = world.getHighestBlockAt(loc)
            loc.y = block.y.toDouble() + 1

            player.teleport(loc)
            player.resetAttributes(true)

            // Grace period resistance
            player.addPotionEffect(PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 10 * 20, 4, true, false))
        }

        lifecycle.game.playSound(Sound.ENDERDRAGON_GROWL, 1.0f)

        // Refill nearby chests for deathmatch
        val deathmatchChests = mutableListOf<Chest>()
        for (chunk in world.loadedChunks)
        {
            for (te in chunk.tileEntities)
            {
                if (te is Chest)
                {
                    val dist = te.location.distance(spawn)
                    if (dist <= 50)
                    {
                        te.inventory.clear()
                        deathmatchChests.add(te)
                    }
                }
            }
        }

        HGLootGenerator.fillChestsFromDataSync(deathmatchChests, HGLootType.REFILL)

        // Grace period countdown
        Schedulers
            .sync()
            .runRepeating({ task ->
                if (!lifecycle.deathmatch || lifecycle.deathmatchGracePeriod <= 0)
                {
                    task.close()
                    lifecycle.game.sendMessage("${CC.RED}${CC.BOLD}FIGHT!")
                    lifecycle.game.playSound(Sound.NOTE_PLING, 2.0f)
                    return@runRepeating
                }

                lifecycle.game.sendMessage("${CC.YELLOW}Grace period ends in ${CC.RED}${lifecycle.deathmatchGracePeriod}${CC.YELLOW}s...")
                lifecycle.deathmatchGracePeriod--
            }, 0L, 20L)
            .bindWith(lifecycle.game)
    }
}
