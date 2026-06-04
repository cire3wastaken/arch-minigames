package mc.arch.minigames.arcade

import com.cryptomorin.xseries.XMaterial
import gg.tropic.practice.minigame.MiniGameMode
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.provider.MiniProviderVersion
import net.evilblock.cubed.util.bukkit.ItemBuilder

enum class ArcadeMode(
    override val teamSize: Int,
    val minimumPlayersRequiredToEnterStarting: Int,
    val minimumPlayersRequiredToEnterFastForward: Int,
    val startGameCountDown: Int,
    override val teamCount: Int,
    override val providerVersion: MiniProviderVersion,
    val modeId: String,
    val kitId: String,
    val queueId: String,
    val mapGroup: String,
    val displayName: String,
    val modeLabel: String,
    val iconMaterial: String, // Keep string XMaterial pulls in Bukkit which is absent on duels app
    val description: List<String>,
    val modeDescription: List<String>
) : MiniGameMode
{
    SUMO(
        teamSize = 24,
        minimumPlayersRequiredToEnterStarting = 10,
        minimumPlayersRequiredToEnterFastForward = 18,
        startGameCountDown = 121,
        teamCount = 1,
        providerVersion = MiniProviderVersion.LEGACY,
        modeId = "sumo",
        kitId = "sumo_arcade",
        queueId = "sumo_arcade:Casual:24v24",
        mapGroup = "arcade_sumo",
        displayName = "Sumo",
        modeLabel = "Quick Play",
        iconMaterial = "LEAD",
        description = listOf(
            "No items, no armor.",
            "Just you, a stick, and a ledge.",
            "Last one standing wins."
        ),
        modeDescription = listOf("Knock the other player off the platform!")
    )
    {
        override fun maxPlayers() = 24
    },
    OITC(
        teamSize = 24,
        minimumPlayersRequiredToEnterStarting = 4,
        minimumPlayersRequiredToEnterFastForward = 12,
        startGameCountDown = 121,
        teamCount = 1,
        providerVersion = MiniProviderVersion.LEGACY,
        modeId = "oitc",
        kitId = "oitc_arcade",
        queueId = "oitc_arcade:Casual:24v24",
        mapGroup = "arcade_oitc",
        displayName = "One in the Chamber",
        modeLabel = "Quick Play",
        iconMaterial = "BOW",
        description = listOf(
            "Each shot is a one-hit kill.",
            "Run out of arrows? Punch.",
            "Be the last one alive."
        ),
        modeDescription = listOf("One in the chamber - bow shots are instant kills!")
    )
    {
        override fun maxPlayers() = 24
    },
    RED_LIGHT_GREEN_LIGHT(
        teamSize = 16,
        minimumPlayersRequiredToEnterStarting = 6,
        minimumPlayersRequiredToEnterFastForward = 12,
        startGameCountDown = 181,
        teamCount = 1,
        providerVersion = MiniProviderVersion.MODERN,
        modeId = "rlgl",
        kitId = "rlgl_arcade",
        queueId = "rlgl_arcade:Casual:16v16",
        mapGroup = "arcade_rlgl",
        displayName = "Red Light, Green Light",
        modeLabel = "Quick Play",
        iconMaterial = "RED_DYE",
        description = listOf(
            "Race to the finish line —",
            "but never move while the",
            "light is red, or you're out."
        ),
        modeDescription = listOf("Cross the finish on green, freeze on red.")
    )
    {
        override fun maxPlayers() = 16
    };

    val icon: XMaterial
        get() = XMaterial.valueOf(iconMaterial)

    fun toModeMetadata() = MiniGameModeMetadata(
        id = modeId,
        description = modeDescription.joinToString(" "),
        queueId = queueId,
        displayName = displayName,
        displayItem = ItemBuilder.of(icon).build(),
        mapGroup = mapGroup,
        kitID = kitId,
        mode = this,
        npcSkinValue = Skins.HALL_SKIN_VALUE,
        npcSkinSignature = Skins.HALL_SKIN_SIGNATURE
    )
}
