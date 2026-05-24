package mc.arch.minigame.pof

enum class PofGameFormat(val teamCount: Int)
{
    Solo(teamCount = 8),
    Duos(teamCount = 6)
}
