package gg.tropic.practice.configuration

import gg.scala.commons.graduation.Schoolmaster
import gg.scala.commons.persist.datasync.DataSyncKeys
import gg.scala.commons.persist.datasync.DataSyncService
import gg.scala.commons.persist.datasync.DataSyncSource
import gg.scala.commons.spatial.Position
import gg.scala.flavor.service.Service
import gg.tropic.practice.minigame.MiniGameTypeProvider
import gg.tropic.practice.namespace
import gg.tropic.practice.namespaceShortened
import gg.tropic.practice.parkour.ParkourConfiguration
import gg.tropic.practice.suffixWhenDev
import net.kyori.adventure.key.Key
import org.bukkit.entity.Player

/**
 * @author GrowlyX
 * @since 10/13/2023
 */
@Service
object PracticeConfigurationService : DataSyncService<PracticeConfiguration>()
{
    object LobbyConfigurationKeys : DataSyncKeys
    {
        override fun newStore() = "mi-practice-lobbyconfig"

        override fun store() = Key.key(namespace(), "lobbyconf")
        override fun sync() = Key.key(namespaceShortened().suffixWhenDev(), "lcsync")
    }

    private val schoolMaster = Schoolmaster<PracticeConfiguration>().apply {
        stage("migrate-lobby-configurations") {
            minigameConfigurations = mutableMapOf()
            minigameConfigurations["duels"] = MinigameLobbyConfiguration(
                spawnLocation = spawnLocation,
                loginMOTD = loginMOTD,
                parkourConfiguration = parkourConfiguration ?: ParkourConfiguration()
            )
        }

        stage("add-editable-uis") {
            editableUIs = mutableMapOf()
        }

        stage("add-minigame-lobby-npcs") {
            minigameConfigurations.forEach { _, value ->
                value.minigameLobbyNPCs = mutableListOf()
            }
        }

        stage("add-quests-v2") {
            minigameConfigurations.forEach { _, value ->
                value.quests = mutableMapOf()
            }
        }

        stage("add-quest-master") {
            minigameConfigurations.forEach { _, value ->
                value.questMasterLocation = Position(
                    0.0, 0.0, 0.0, 180.0F, 0.0F
                )
            }
        }

        stage("add-bezier-teleporters-v2") {
            minigameConfigurations.forEach { _, value ->
                value.bezierTeleporters = mutableListOf()
            }
        }

        stage("core-holographic-stats") {
            minigameConfigurations.forEach { _, value ->
                value.coreHolographicStatsPosition = Position(
                    0.0, 100.0, 0.0, 180.0F, 0.0F
                )
            }
        }

        stage("quest-req-description") {
            minigameConfigurations.forEach { _, value ->
                value.quests.values.forEach { quest ->
                    quest.requirements.forEach { requirement ->
                        requirement.description = "Daily wins"
                    }
                }
            }
        }

        stage("lobbynpc-newly-released") {
            minigameConfigurations.forEach { _, value ->
                value.minigameLobbyNPCs.forEach { quest ->
                    quest.newlyReleased = false
                }
            }
        }

        stage("lobbynpc-broadcast-label") {
            minigameConfigurations.forEach { _, value ->
                value.minigameLobbyNPCs.forEach { quest ->
                    quest.broadcastLabel = "NEWLY RELEASED"
                }
            }
        }

        stage("lobbynpc-aciton-label") {
            minigameConfigurations.forEach { _, value ->
                value.minigameLobbyNPCs.forEach { quest ->
                    quest.actionLabel = "CLICK TO PLAY"
                }
            }
        }

        stage("lobbynpc-replacement-syntax") {
            minigameConfigurations.forEach { _, value ->
                value.minigameLobbyNPCs.forEach { quest ->
                    quest.replacement = quest.replacement + " playing"
                }
            }
        }

        stage("add-ext-baseurl") {
            externalPlayerCountBaseUrl = "http://external-playercount-app.default.svc.cluster.local:8080"
        }

        stage("add-levitation-portals") {
            minigameConfigurations.forEach { (_, value) ->
                value.levitationPortals = mutableListOf()
            }
        }

        stage("add-rankgift-leaderboard") {
            minigameConfigurations.forEach { (_, value) ->
                value.rankGiftLeaderboardLocation = null
            }
        }

        stage("add-rankgift-NPCs") {
            minigameConfigurations.forEach { (_, value) ->
                value.rankGiftTop3NPCs = mutableListOf<Position?>()
            }
        }
    }

    internal var typeProvider: MiniGameTypeProvider? = null
    fun registerTypeProvider(provider: MiniGameTypeProvider)
    {
        typeProvider = provider
    }

    fun openEditableUI(player: Player, name: String)
    {
        val editableUI = cached().editableUIs[name]
            ?: return

        editableUI.compose().openMenu(player)
    }

    fun minigameType() = typeProvider!!
    fun minigameTypeOrNull() = typeProvider
    fun local() = cached().local()

    override fun postReload()
    {
        val cached = cached()
        if (schoolMaster.mature(cached))
        {
            sync(cached)
        }
    }

    override fun locatedIn() = DataSyncSource.Mongo

    override fun keys() = LobbyConfigurationKeys
    override fun type() = PracticeConfiguration::class.java
}
