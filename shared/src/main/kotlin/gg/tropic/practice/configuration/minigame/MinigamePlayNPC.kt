package gg.tropic.practice.configuration.minigame

import gg.scala.commons.spatial.Position

/**
 * @author Subham
 * @since 6/24/25
 */
data class MinigamePlayNPC(
    var associatedGameMode: String = "",
    var displayName: String = "",
    var position: Position = Position(0.0, 0.0, 0.0)
)
