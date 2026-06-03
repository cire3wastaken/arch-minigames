package mc.arch.minigames.arcade

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.MiniGameTypeMetadata

/**
 * Skin used for the Arcade auto-join NPC / hub head.
 */
object Skins
{
    val HALL_SKIN_VALUE =
        "ewogICJ0aW1lc3RhbXAiIDogMTczMDAwODEyMzA4NCwKICAicHJvZmlsZUlkIiA6ICI5MWYwNGZlOTBmMzY0M2I1OGYyMGUzMzc1Zjg2ZDM5ZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdG9ybVN0b3JteSIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS80ODhmNGJjY2UzMTJjZjliZjEwMDc3YjE4NWVhZjg4ZDM0ZDZmMTdlYzNhZmJhYjAyZjI4NDZjODljYzdhODUwIgogICAgfQogIH0KfQ=="
    val HALL_SKIN_SIGNATURE =
        "OirTt/V9GA4sPiTaGmjQsPwE49FhvenMxn8cjQJJs+PXrx7Sn/KUw02ZCJIkDdUP9p90JgnTr2V72b/wU/aceicKexXGeIMXSFp4KWUSTOmHbmPip6v42UlA3UqrMDoHgua80nqZgYCP1lClckUVu9bS3XXQ+lUw2jlzQkssxKb7qsqvWC91OF9agXEiS/kc6kiBDOoT0+AVZFKVpTskOd6Jc9J/MLiWVoIvsuJdnmS54N1wSUodbVTk7/swe1y69bNEAbxHNxEq9Biv0Zu5XcapKgvY8aQL6B1HZe6C2nApzXh388b3jApV5kS4Kuv8XwLEJH+ON1AjAFQSnrtbbA+X1NW8jAAUBRV7lwVSbylyje+GPlotRH7XyFFcGFKg0QmQP/SxPJMdgxALedzZkHsr84U2wfnl5zVmoTghYWs3ql7XgmCE66SIfrmyRvGPHChwA7QW0mBqBe3HIGfBk8+t1pJu8zBriDDm1B4vAMsf43QQK20A3XRW6OxwTTDFu5k8i9NXrJhyarp82/V+KqwwsASZhRgP82swFDZytk/ursbUVuLMv5ebkZIC9/49bybErN7EeIjnWOlEUgbeBzg6GU6FYOjBL2GhT5s6MH7OqquFDtsQLj/ESamIL2lVckT9Li8L6D501qeT3bgPS8jpvLsVo8mlUoSL2EcplhE="
}

object ArcadeTypeMetadata : MiniGameTypeMetadata(
    internalId = "arcade",
    displayName = "Arcade",
    item = XMaterial.JUKEBOX,
    lobbyGroup = "arcadelobby",
    autoJoinSkinValue = Skins.HALL_SKIN_VALUE,
    autoJoinSkinSignature = Skins.HALL_SKIN_SIGNATURE,
    gameModes = ArcadeMode.entries.associateTo(LinkedHashMap()) { it.modeId to it.toModeMetadata() }
)
{

    fun registerExternalModes(prefix: String, modes: Map<String, MiniGameModeMetadata>)
    {
        @Suppress("UNCHECKED_CAST")
        val mutable = gameModes as MutableMap<String, MiniGameModeMetadata>
        modes.forEach { (id, metadata) -> mutable.putIfAbsent("${prefix}_$id", metadata) }
    }
}
