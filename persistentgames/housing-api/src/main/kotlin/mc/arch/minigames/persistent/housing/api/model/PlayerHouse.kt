package mc.arch.minigames.persistent.housing.api.model

import gg.scala.common.Savable
import gg.scala.commons.annotations.Model
import gg.scala.store.storage.storable.IDataStoreObject
import gg.tropic.practice.ugc.HostedWorldAttribute
import mc.arch.minigames.persistent.housing.api.action.HousingActionService
import mc.arch.minigames.persistent.housing.api.categorization.model.CategorizationResult
import mc.arch.minigames.persistent.housing.api.action.player.ActionEvent
import mc.arch.minigames.persistent.housing.api.action.tasks.Task
import mc.arch.minigames.persistent.housing.api.content.HousingBiome
import mc.arch.minigames.persistent.housing.api.content.HousingGameMode
import mc.arch.minigames.persistent.housing.api.content.HousingItemStack
import mc.arch.minigames.persistent.housing.api.content.HousingTime
import mc.arch.minigames.persistent.housing.api.content.HousingWeather
import mc.arch.minigames.persistent.housing.api.entity.HousingHologram
import mc.arch.minigames.persistent.housing.api.entity.HousingNPC
import mc.arch.minigames.persistent.housing.api.island.HousingMapType
import mc.arch.minigames.persistent.housing.api.role.HouseRole
import mc.arch.minigames.persistent.housing.api.service.PlayerHousingService
import mc.arch.minigames.persistent.housing.api.spatial.WorldPosition
import net.evilblock.cubed.util.bukkit.cuboid.Cuboid
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * @author Subham
 * @since 9/8/25
 */
@Model
data class PlayerHouse(
    val owner: UUID,
    override var displayName: String,
    override var name: String = displayName.lowercase(),
    var spawnPoint: WorldPosition? = null,
    var defaultGamemode: HousingGameMode = HousingGameMode.SURVIVAL,
    var maxPlayers: Int = 20,
    var plotSizeBlocks: Int = 400,
    var tags: MutableList<String> = mutableListOf(),
    val actionEventMap: MutableMap<String, MutableList<Task>> = mutableMapOf(),
    val roles: MutableMap<String, HouseRole> = HouseRole.defaults(),
    val visitationStatuses: MutableMap<VisitationStatus, Boolean> = mutableMapOf(
        VisitationStatus.PRIVATE to true,
    ),
    val housingBans: MutableList<UUID> = mutableListOf(),
    val playerRoles: MutableMap<UUID, String> = mutableMapOf(
        owner to "owner"
    ),
    val lastLaunched: Long = System.currentTimeMillis(),
    val houseIcon: HousingItemStack? = null,
    var map: HousingMapType = HousingMapType.CITY,
    var music: String? = null,
    var hasBeenSetup: Boolean = false,
    var allowsMutatingOutsideRegion: Boolean? = false,
    var housingTime: HousingTime? = null,
    var housingWeather: HousingWeather? = null,
    var housingBiome: HousingBiome? = null,
    var region: Cuboid? = null,
    val houseNPCMap: MutableMap<String, HousingNPC> = mutableMapOf(),
    val houseHologramMap: MutableMap<String, HousingHologram> = mutableMapOf(),
    var derivedCategories: CategorizationResult? = null,
    override val identifier: UUID = UUID.randomUUID(),
    override val description: MutableList<String> = mutableListOf()
) : IDataStoreObject, HostedWorldAttribute, Savable
{
    fun getAllActionEventsBy(clazz: Class<*>) = actionEventMap.entries.filter {
        HousingActionService.getByName(it.key)?.eventClass() == clazz
    }

    fun addTaskToAction(actionEvent: ActionEvent, task: Task)
    {
        val currentTasks = actionEventMap[actionEvent.id()]
            ?: mutableListOf()

        currentTasks += task
        actionEventMap[actionEvent.id()] = currentTasks
        save()
    }

    fun removeTaskFromAction(task: Task, actionEvent: ActionEvent)
    {
        val currentTasks = actionEventMap[actionEvent.id()]
            ?: mutableListOf()

        currentTasks -= task
        actionEventMap[actionEvent.id()] = currentTasks
        save()
    }

    fun visitationStatusApplies(status: VisitationStatus) = visitationStatuses[status] == true

    fun playerCanJoin(uuid: UUID): CompletableFuture<Boolean> {
        if (uuid != owner && housingBans.contains(uuid))
        {
            return CompletableFuture.completedFuture(false)
        }

        if (visitationStatuses[VisitationStatus.PUBLIC] == true)
        {
            return CompletableFuture.completedFuture(true)
        }

        if (visitationStatuses[VisitationStatus.PRIVATE] == true)
        {
            return CompletableFuture.completedFuture(playerIsOrAboveAdministrator(uuid))
        }

        return CompletableFuture.completedFuture(uuid == owner)
    }

    fun playerIsOrAboveAdministrator(player: UUID): Boolean = owner == player || hasPermission(player, "house.manage")

    fun hasPermission(player: UUID, permission: String) = getRole(player).permissions.contains(permission)

    fun getRoleByName(name: String) = roles[name.lowercase()]

    fun getRole(player: UUID): HouseRole {
        val defaultRole = roles.values.first { it.default }
        val roleString = playerRoles[player]
            ?: return defaultRole

        return getRoleByName(roleString) ?: defaultRole
    }

    override fun save(): CompletableFuture<Void> = PlayerHousingService.save(this)
}
