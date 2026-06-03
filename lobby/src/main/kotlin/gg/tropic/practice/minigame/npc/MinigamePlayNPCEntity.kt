package gg.tropic.practice.minigame.npc

import gg.tropic.practice.configuration.PracticeConfigurationService
import gg.tropic.practice.configuration.minigame.MinigamePlayNPC
import gg.tropic.practice.minigame.MiniGameModeMetadata
import gg.tropic.practice.minigame.joinGame
import gg.tropic.practice.minigame.menu.MinigameNPCModeSelectorMenu
import gg.tropic.practice.minigame.menu.MinigameNPCPlayMenu
import me.lucko.helper.cooldown.Cooldown
import me.lucko.helper.cooldown.CooldownMap
import net.evilblock.cubed.entity.EntityHandler
import net.evilblock.cubed.entity.npc.NpcEntity
import net.evilblock.cubed.util.CC
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.collections.listOf

/**
 * @author Subham
 * @since 6/29/25
 */
class MinigamePlayNPCEntity(
    val configuration: MinigamePlayNPC,
    val isAutoJoin: Boolean = configuration.associatedGameMode == "autojoin",
    val modeMetadatas: List<MiniGameModeMetadata> = configuration.associatedGameMode
        .split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { id ->
            PracticeConfigurationService
                .minigameType()
                .provide()
                .modeNullable(id)
        }
) : NpcEntity(
    lines = when
    {
        isAutoJoin -> autoJoinHeader()
        modeMetadatas.size > 1 -> groupHeader(configuration, modeMetadatas)
        modeMetadatas.size == 1 -> modeMetadatas.first().toNPCHeader()
        else -> nonExistentHeader()
    },
    location = configuration.position.toLocation(
        Bukkit.getWorlds().first()
    )
)
{
    val modeMetadata: MiniGameModeMetadata? = modeMetadatas.firstOrNull()
    val isMultiMode: Boolean = modeMetadatas.size > 1

    init
    {
        persistent = false
    }

    fun currentHeader() = if (isMultiMode)
    {
        groupHeader(configuration, modeMetadatas)
    } else
    {
        modeMetadatas.first().toNPCHeader()
    }

    fun configure()
    {
        initializeData()
        EntityHandler.trackEntity(this)

        if (modeMetadata != null)
        {
            updateLines(currentHeader())
            updateTexture(
                modeMetadata.npcSkinValue,
                modeMetadata.npcSkinSignature
            )
        } else
        {
            if (isAutoJoin)
            {
                val configuration = PracticeConfigurationService.minigameType().provide()
                updateTexture(
                    configuration.autoJoinSkinValue,
                    configuration.autoJoinSkinSignature
                )
                updateLines(generateAutoJoinLines())
            } else
            {
                updateTexture(
                    "ewogICJ0aW1lc3RhbXAiIDogMTc0NTYwNjQ0MjQzMCwKICAicHJvZmlsZUlkIiA6ICI2YWVmMjM3Y2RhNDY0Y2QzYTdiZDcxYjg3YzFlMDEwNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJLYWlqdW5va2kiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTI5YjRiZDM5MzVhM2VlZTJiZjhjZmI3ZWM4M2NlNTdlY2IxNDg4OGU1ZTJiYzc1MzE5NDU2ZjJjYzYxMjU5YyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9",
                    "wF5EANk0hpmwjlAPzUehnejZWYw3hft/65NaTRv8/olx9eHQMNyRAiQ4nnHJPp6DVyc/0Huwk5ve5653P9MZlDaGF7b6VJ6si/BteR2yCYLEGlhv8DQsQhp8cC7bojUwxn0u7j7l8NQOae0uuEv8yh1s6oLzSKIfnYxc05YttBt528ET8JQk7W3UepCOAUvvnC4mSHpj3QY4Qu/6AeD2wQqZ/V+gugtTBD1uKgSnC5Jq3r7h4T5OjVXMpyGVP4KScHDhGqmq+HOxzEZ+iu2m4mnIaxVOjOLgc3lWpMKiFpUevfHg2QywS3DXnaZCg1kh8d8SOOtBRuDhHwREAJKjkK2yZK7d5hp1LZOyEAPs3T3fj16bh+lGeZeC/Rw3khlWYnuATTGNV7zNrKH+E+iZCRazhU/NW738KFsF4BLouBN2YrvMIdhWJF/YtOtYMOJuQ5jZiBbQontvUKurTgD/V/Nq15GQMZQek7lERiGOxH6zoyqoX8NkeeQeqj3KEHdm9t0/o1WZt3S63YAA0+2OrSrTJr7zNITkrveRNEOk8l/0drGTl2sVz3z5XrCVnlDPj81Z+IC/kddhTXwF756Jqi1X4XFkH0FFQUvp2C5BGnwzAl4XcjfFK9GQ02zzkuTVkvuCsC/5NpYia+IOnQpR947wyGWZXgWx0RWep93QCc8=",
                )
                updateLines(generateNonExistentLines())
            }
        }

        updateForCurrentWatchers()
    }

    fun generateNonExistentLines() = nonExistentHeader()

    fun generateAutoJoinLines() = autoJoinHeader()

    companion object
    {
        fun nonExistentHeader() = listOf(
            "${CC.B_YELLOW}CLICK TO PLAY",
            "${CC.RED}???",
            "${CC.B_YELLOW}0 players"
        )

        fun autoJoinHeader() = listOf(
            "${CC.B_YELLOW}CLICK TO PLAY",
            "${CC.RED}Join Random Game",
            PracticeConfigurationService.minigameType()
                .provide()
                .totalPlayersPlaying()
                .let { players ->
                    "${CC.B_YELLOW}$players player${
                        if (players == 1) "" else "s"
                    }"
                }
        )

        fun groupTitle(
            configuration: MinigamePlayNPC,
            modes: List<MiniGameModeMetadata>
        ) = configuration.displayName.ifBlank {
            modes.joinToString(" / ") { it.displayName }
        }

        fun groupHeader(
            configuration: MinigamePlayNPC,
            modes: List<MiniGameModeMetadata>
        ): List<String>
        {
            val players = modes.sumOf { it.playersPlaying() }
            return listOf(
                "${CC.B_YELLOW}CLICK TO PLAY",
                "${CC.RED}${groupTitle(configuration, modes)}",
                "${CC.B_YELLOW}$players player${if (players == 1) "" else "s"}"
            )
        }
    }

    private val cooldowns = CooldownMap.create<UUID>(
        Cooldown.ofTicks(20L)
    )

    fun onPortal(player: Player)
    {
        if (!cooldowns.test(player.uniqueId))
        {
            player.sendMessage("${CC.RED}Slow down! You are trying to join a game too fast.")
            return
        }

        if (modeMetadata == null)
        {
            player.sendMessage("${CC.D_GRAY}Coming soon...")
            return
        }

        if (isMultiMode)
        {
            MinigameNPCModeSelectorMenu(
                groupTitle(configuration, modeMetadatas),
                modeMetadatas
            ).openMenu(player)
            return
        }

        modeMetadata.joinGame(player)
    }

    override fun onLeftClick(player: Player)
    {
        onRightClick(player)
    }

    override fun onRightClick(player: Player)
    {
        if (modeMetadata == null)
        {
            if (isAutoJoin)
            {
                if (!cooldowns.test(player.uniqueId))
                {
                    player.sendMessage("${CC.RED}Slow down! You are trying to join a game too fast.")
                    return
                }

                player.sendMessage("${CC.GRAY}Finding a game for you to join...")

                PracticeConfigurationService
                    .minigameType()
                    .provide()
                    .computeGameTypeRequiringPlayers()
                    .thenAccept { metadata ->
                        metadata.joinGame(player)
                    }
                return
            }

            player.sendMessage("${CC.D_GRAY}Coming soon...")
            return
        }

        if (isMultiMode)
        {
            MinigameNPCModeSelectorMenu(
                groupTitle(configuration, modeMetadatas),
                modeMetadatas
            ).openMenu(player)
            return
        }

        MinigameNPCPlayMenu(modeMetadata).openMenu(player)
    }
}
