package mc.arch.minigame.pof

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
        displayName = "Solo"
    ),
    DUOS(
        teamSize = 2,
        format = PofGameFormat.Duos,
        minimumPlayersRequiredToEnterStarting = 4,
        minimumPlayersRequiredToEnterFastForward = 8,
        startGameCountDown = 45,
        displayName = "Duos"
    );

    override fun maxPlayers() = format.teamCount * teamSize
}
