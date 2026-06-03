package mc.arch.minigames.arcade.lobby.menu

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import gg.tropic.practice.provider.MiniProviderVersion
import mc.arch.minigame.miniwalls.MiniWallsGameType
import mc.arch.minigames.arcade.ArcadeMode
import mc.arch.minigames.hungergames.HungerGamesTypeMetadata
import mc.arch.minigames.skywars.SkyWarsTypeMetadata

data class ArcadeCardMode(
    val displayName: String,
    val lore: List<String>,
    val queueId: String,
    val providerVersion: MiniProviderVersion
)

data class ArcadeCard(
    val internalId: String,
    val cardName: String,
    val icon: XMaterial,
    val slot: Int,
    val lore: List<String>,
    val modes: List<ArcadeCardMode>
)

object ArcadeCatalog
{
     private data class Presentation(
        val icon: XMaterial,
        val slot: Int,
        val lore: List<String>
    )

    private val externalPresentation = mapOf(
        "hungergames" to Presentation(
            XMaterial.IRON_SWORD, 11,
            listOf(
                "Loot chests, gear up, and",
                "outlast every other tribute",
                "to claim victory."
            )
        ),
        "skywars" to Presentation(
            XMaterial.FEATHER, 20,
            listOf(
                "Spawn on an island, raid",
                "the middle, and knock",
                "everyone else off."
            )
        ),
        "miniwalls" to Presentation(
            XMaterial.COBBLESTONE, 29,
            listOf(
                "Four teams, four withers.",
                "Break the walls, raid your",
                "enemies, be the last team",
                "standing."
            )
        ),
    )

    private val nativeSlots = mapOf(
        ArcadeMode.SUMO to 14,
        ArcadeMode.OITC to 15,
        ArcadeMode.RED_LIGHT_GREEN_LIGHT to 23,
    )

    val cards: List<ArcadeCard> by lazy {
        externalCards() + nativeCards()
    }

    private fun nativeCards() = ArcadeMode.entries.map { mode ->
        ArcadeCard(
            internalId = ArcadeTypeMetadataInternalId,
            cardName = mode.displayName,
            icon = mode.icon,
            slot = nativeSlots[mode] ?: 0,
            lore = mode.description,
            modes = listOf(
                ArcadeCardMode(
                    displayName = mode.modeLabel,
                    lore = mode.modeDescription,
                    queueId = mode.queueId,
                    providerVersion = mode.providerVersion
                )
            )
        )
    }

    private fun externalCards() = listOf(
        HungerGamesTypeMetadata,
        SkyWarsTypeMetadata,
        MiniWallsGameType
    ).mapNotNull { type ->
        val presentation = externalPresentation[type.internalId] ?: return@mapNotNull null
        ArcadeCard(
            internalId = type.internalId,
            cardName = type.displayName,
            icon = presentation.icon,
            slot = presentation.slot,
            lore = presentation.lore,
            modes = type.gameModes.values.map { mode ->
                ArcadeCardMode(
                    displayName = mode.displayName,
                    lore = listOf(mode.description),
                    queueId = mode.queueId,
                    providerVersion = mode.mode.providerVersion
                )
            }
        )
    }

    private const val ArcadeTypeMetadataInternalId = "arcade"
}
