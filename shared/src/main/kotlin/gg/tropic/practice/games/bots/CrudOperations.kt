package gg.tropic.practice.games.bots

import gg.tropic.practice.namespace
import net.evilblock.cubed.ScalaCommonsSpigot
import net.evilblock.cubed.serializers.Serializers
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * @author GrowlyX
 * @since 8/9/2024
 */
fun String.extractBotGameMetadata() = Serializers.gson.fromJson(this, BotGameMetadata::class.java)

fun getBotMetdataOfPlayer(player: UUID) = ScalaCommonsSpigot.instance.kvConnection.sync()
    .get(
        "${namespace()}:bot-metadata:$player"
    )
    ?.extractBotGameMetadata()

fun deleteBotMetadataOfPlayer(player: UUID) = ScalaCommonsSpigot.instance.kvConnection.sync()
    .del(
        "${namespace()}:bot-metadata:$player"
    )

/**
 * Persists this metadata to the KV store. The underlying Lettuce call is
 * synchronous (a blocking Redis round-trip), so it is wrapped in a
 * [CompletableFuture] to keep it off the calling thread — most importantly the
 * main server thread, where a profile showed this blocking inside GameStopTask's
 * player-interact handler. Callers that depend on the write being visible should
 * chain off the returned future rather than fire-and-forget.
 */
fun BotGameMetadata.storeForUser(player: UUID): CompletableFuture<String> {
    val key = "${namespace()}:bot-metadata:$player"
    val json = Serializers.gson.toJson(this)
    return CompletableFuture.supplyAsync {
        ScalaCommonsSpigot.instance.kvConnection.sync().set(key, json)
    }
}
