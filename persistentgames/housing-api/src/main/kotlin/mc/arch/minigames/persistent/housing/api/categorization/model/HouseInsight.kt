package mc.arch.minigames.persistent.housing.api.categorization.model

/**
 * Atomic statement extracted from a house's textual fields - the "insight" stage
 * of the multi-stage pipeline. Each insight covers a single observable aspect of
 * the house and carries a normalized sentiment / salience label.
 *
 * Mirrors the insight-extraction step of the App Store review summarization
 * pipeline (Apple ML research, 2024): one sentence, one topic, one label.
 */
data class HouseInsight(
    val statement: String,
    val sentiment: Sentiment,
    val confidence: Double,
    val sourceField: String
)
{
    enum class Sentiment
    {
        POSITIVE, NEGATIVE, NEUTRAL, DESCRIPTIVE
    }
}
