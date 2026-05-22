package mc.arch.minigames.persistent.housing.api.categorization.model

/**
 * Final derived category for a house - stage 3 output. Categories are
 * dynamically derived, not drawn from a fixed enum, so emergent themes can
 * surface without a code change.
 *
 * The [scope] mirrors the App Store pipeline's split between in-app and
 * out-of-app experiences: IN_HOUSE categories describe the house itself
 * (Parkour, Roleplay, Hub…) and META categories describe social/meta
 * aspects (Friends-Only, Showcase…) which are deprioritized in discovery.
 */
data class HouseCategory(
    val label: String,
    val confidence: Double,
    val scope: Scope,
    val supportingTopicNames: List<String>
)
{
    enum class Scope
    {
        IN_HOUSE, META
    }
}
