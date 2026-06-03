package mc.arch.minigames.pof

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.configuration.minigame.type.LobbyNPCSkinType
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.MiniGameTypeMetadata
import net.evilblock.cubed.util.bukkit.ItemBuilder

object PofGameType : MiniGameTypeMetadata(
    internalId = "pof",
    displayName = "Pillar of Fortune",
    item = XMaterial.GOLD_BLOCK,
    lobbyGroup = "arcadelobby",
    autoJoinSkinValue = LobbyNPCSkinType.POF.value,
    autoJoinSkinSignature = LobbyNPCSkinType.POF.signature,
    gameModes = mapOf(
        "solo" to MiniGameModeMetadata(
            id = "solo",
            description = "Loot rains down — outlast everyone!",
            queueId = "pof_main:Casual:1v1",
            displayName = "Solo (Modern)",
            displayItem = ItemBuilder
                .of(XMaterial.GOLD_BLOCK)
                .build(),
            mapGroup = "pof_main",
            kitID = "pof_main",
            mode = PofMode.SOLO,
            npcSkinValue = LobbyNPCSkinType.POF.value,
            npcSkinSignature = LobbyNPCSkinType.POF.signature
        ),
        "solo_legacy" to MiniGameModeMetadata(
            id = "solo_legacy",
            description = "Loot rains down — outlast everyone! (1.8)",
            queueId = "legacy_pof_main:Casual:1v1",
            displayName = "Solo (Legacy)",
            displayItem = ItemBuilder
                .of(XMaterial.GOLD_INGOT)
                .build(),
            mapGroup = "pof_legacy_main",
            kitID = "legacy_pof_main",
            mode = PofMode.SOLO_LEGACY,
            npcSkinValue = LobbyNPCSkinType.POF.value,
            npcSkinSignature = LobbyNPCSkinType.POF.signature
        )
    )
)
