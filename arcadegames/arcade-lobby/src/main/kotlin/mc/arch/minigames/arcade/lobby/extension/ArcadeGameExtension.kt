package mc.arch.minigames.arcade.lobby.extension

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MinigameCompetitiveCustomizer
import org.bukkit.entity.Player

interface ArcadeGameExtension
{
    val internalId: String

    fun manageButton(player: Player): ArcadeManageButton? = null

    fun onLobbyJoin(player: Player) {}

    fun competitive(): MinigameCompetitiveCustomizer? = null
}

data class ArcadeManageButton(
    val displayName: String,
    val lore: List<String>,
    val icon: XMaterial,
    val onClick: (Player) -> Unit
)
