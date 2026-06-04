package mc.arch.minigames.pof

import gg.tropic.practice.minigame.MiniGameMode
import gg.tropic.practice.provider.MiniProviderVersion

enum class PofMode(
    override val teamSize: Int,
    val format: PofGameFormat,
    val minimumPlayersRequiredToEnterStarting: Int,
    val minimumPlayersRequiredToEnterFastForward: Int,
    val startGameCountDown: Int,
    val displayName: String,
    override val providerVersion: MiniProviderVersion = MiniProviderVersion.MODERN,
    override val teamCount: Int = format.teamCount
) : MiniGameMode
{
    SOLO(
        teamSize = 1,
        format = PofGameFormat.Solo,
        minimumPlayersRequiredToEnterStarting = 4,
        minimumPlayersRequiredToEnterFastForward = 6,
        startGameCountDown = 30,
        displayName = "Solo (Modern)",
        providerVersion = MiniProviderVersion.MODERN
    ),
    SOLO_LEGACY(
        teamSize = 1,
        format = PofGameFormat.Solo,
        minimumPlayersRequiredToEnterStarting = 4,
        minimumPlayersRequiredToEnterFastForward = 6,
        startGameCountDown = 30,
        displayName = "Solo (Legacy)",
        providerVersion = MiniProviderVersion.LEGACY
    );

    override fun maxPlayers() = format.teamCount * teamSize
}
