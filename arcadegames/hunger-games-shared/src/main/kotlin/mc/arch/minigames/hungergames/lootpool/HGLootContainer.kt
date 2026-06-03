package mc.arch.minigames.hungergames.lootpool

/**
 * @author ArchMC
 */
data class HGLootScopeContainer(
    var fillPercentage: Double = 0.60,
    val candidates: MutableList<HGLootCandidate> = mutableListOf()
)

data class HGLootContainer(
    val types: MutableMap<HGLootType, HGLootScopeContainer> = mutableMapOf(
        HGLootType.INITIAL to HGLootScopeContainer(),
        HGLootType.REFILL to HGLootScopeContainer()
    )
)
