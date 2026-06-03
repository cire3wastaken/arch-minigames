package gg.tropic.practice

import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.commons.playerstatus.PlayerStatusTrackerService
import gg.scala.lemon.util.QuickAccess
import gg.scala.lemon.util.QuickAccess.username
import net.evilblock.cubed.util.CC
import net.evilblock.cubed.util.ServerVersion
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * @author GrowlyX
 * @since 2/10/2024
 */
var devProvider: () -> Boolean = { false }

fun isDev() = devProvider()
fun isProd() = !devProvider()

fun namespace() = "tropicpractice"
fun namespaceShortened() = "tp"

fun practiceGroup() = "mip"
fun gameGroup() = "mipgame"
fun lobbyGroup() = "miplobby"

fun String.suffixWhenDev() = (if (isDev()) "${if (this == "tropicpractice")
    "tropicprac" else this}dev" else this)

fun isMiniGameServer() = "mipgame" in ServerSync.local.groups
fun isModernDuelsServer() = "duelsmodernlobby" in ServerSync.local.groups

fun isModernKitFormat() = ServerVersion.getVersion().isNewerThanOrEquals(ServerVersion.v1_20)

fun UUID.toDisplayName() = PlayerStatusTrackerService.loadStatusOf(this)
    .join()
    ?.prefixedName
    ?: "${CC.GRAY}${username()}"

fun UUID.toDisplayNameRaw() = QuickAccess.computePrefixedName(this).join()
    ?: "${CC.GRAY}${username()}"

@Throws(IllegalStateException::class)
fun ItemStack.itemTo64(): String?
{
    return runCatching {
        val outputStream = ByteArrayOutputStream()
        val dataOutput = BukkitObjectOutputStream(outputStream)
        dataOutput.writeObject(this)

        // Serialize that array
        dataOutput.close()
        Base64Coder.encodeLines(outputStream.toByteArray())
    }.getOrNull()
}

fun String.itemFrom64(): ItemStack?
{
    return runCatching {
        val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(this))
        val dataInput = BukkitObjectInputStream(inputStream)
        dataInput.use { dataInput ->
            dataInput.readObject() as ItemStack
        }
    }.getOrNull()
}
