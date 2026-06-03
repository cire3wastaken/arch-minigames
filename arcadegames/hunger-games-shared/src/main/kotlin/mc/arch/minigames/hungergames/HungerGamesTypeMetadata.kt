package mc.arch.minigames.hungergames

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.configuration.minigame.type.LobbyNPCSkinType
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.MiniGameTypeMetadata

/**
 * @author ArchMC
 */
object HungerGamesTypeMetadata : MiniGameTypeMetadata(
    internalId = "hungergames",
    displayName = "Survival Games",
    item = XMaterial.IRON_SWORD,
    lobbyGroup = "arcadelobby",
    gameModes = mapOf(
        "solo_normal" to MiniGameModeMetadata(
            id = "solo_normal",
            queueId = "sg_solo_normal:Casual:1v1",
            displayName = "Solo",
            description = "Classic 16-player Survival Games!",
            displayItem = XMaterial.IRON_SWORD.parseItem()!!,
            mapGroup = "sg_solo_normal",
            kitID = "sg_solo_normal",
            mode = HungerGamesMode.SOLO_NORMAL,
            npcSkinValue = LobbyNPCSkinType.SURVIVAL_GAMES.value,
            npcSkinSignature = LobbyNPCSkinType.SURVIVAL_GAMES.signature,
            allowRejoins = false
        ),
        "doubles_normal" to MiniGameModeMetadata(
            id = "doubles_normal",
            queueId = "sg_doubles_normal:Casual:1v1",
            displayName = "Doubles",
            description = "Team up! 12 teams of 2 in Survival Games!",
            displayItem = XMaterial.IRON_SWORD.parseItem()!!,
            mapGroup = "sg_doubles_normal",
            kitID = "sg_doubles_normal",
            mode = HungerGamesMode.DOUBLES_NORMAL,
            npcSkinValue = LobbyNPCSkinType.SURVIVAL_GAMES.value,
            npcSkinSignature = LobbyNPCSkinType.SURVIVAL_GAMES.signature,
            allowRejoins = false
        )
    ),
    autoJoinSkinSignature = LobbyNPCSkinType.SURVIVAL_GAMES.signature,
    autoJoinSkinValue = LobbyNPCSkinType.SURVIVAL_GAMES.value
)
