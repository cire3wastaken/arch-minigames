package gg.tropic.practice.metadata

import gg.scala.commons.ExtendedScalaPlugin
import gg.scala.commons.agnostic.sync.ServerSync
import gg.scala.flavor.inject.Inject
import gg.scala.flavor.service.Configure
import gg.scala.flavor.service.Service
import gg.tropic.practice.games.SystemBulkMetadata
import gg.tropic.practice.games.GameReference
import gg.tropic.practice.namespace
import gg.tropic.practice.suffixWhenDev
import gg.tropic.practice.ugc.HostedWorldInstanceReference
import me.lucko.helper.Schedulers
import net.evilblock.cubed.ScalaCommonsSpigot
import net.evilblock.cubed.serializers.Serializers
import net.evilblock.cubed.services.CommonsServiceExecutor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * @author GrowlyX
 * @since 9/25/2023
 */
@Service(priority = 1600)
object SystemMetadataService
{
    @Inject
    lateinit var plugin: ExtendedScalaPlugin

    fun bindToStatusService(statusService: () -> InstanceMetadata)
    {
        Schedulers
            .async()
            .runRepeating(Runnable {
                MetadataRPC.updateStatus(
                    server = ServerSync.local.id,
                    status = statusService()
                )
            }, 0L, 10L)
            .bindWith(plugin)

        plugin.logger.info("Bound status service. Full instance metadata is broadcast to the gamemanager channel every 0.5 seconds.")
    }

    private var gameStatusWriteLock = ReentrantReadWriteLock()
    private var recentGameStatuses = listOf<GameReference>()
    private var recentHostedWorldInstancesStatuses = listOf<HostedWorldInstanceReference>()

    fun allGames() = gameStatusWriteLock.read { recentGameStatuses }
    fun allHostedWorldInstances() = gameStatusWriteLock.read { recentHostedWorldInstancesStatuses }

    private fun computeAllGameStatuses() = CompletableFuture
        .supplyAsync {
            ScalaCommonsSpigot.Companion.instance.kvConnection
                .sync()
                .get("${namespace().suffixWhenDev()}:gamemanager:bulk-statuses")
                ?.let {
                    Serializers.gson.fromJson(
                        it, SystemBulkMetadata::class.java
                    )
                }
                ?.indexes?.values
        }

    fun getQueued(queueId: String) = Metadata.reader()
        .read("queue:users-queued:$queueId")
        ?.toInt() ?: 0

    fun getPlaying(queueId: String) = Metadata.reader()
        .read("queue:users-playing:$queueId")
        ?.toInt() ?: 0

    @Configure
    fun configure()
    {
        Metadata.reader()
            .withNamespace(namespace())
            .enableAutomatedPull(CommonsServiceExecutor)

        CommonsServiceExecutor.scheduleAtFixedRate({
            gameStatusWriteLock.write {
                val statuses = computeAllGameStatuses().join()
                    ?: return@write run {
                        recentGameStatuses = listOf()
                        recentHostedWorldInstancesStatuses = listOf()
                    }

                recentGameStatuses = statuses
                    .flatMap { it.games }
                recentHostedWorldInstancesStatuses = statuses
                    .flatMap { it.hostedWorldInstanceReferences }
            }
        }, 0L, 500L, TimeUnit.MILLISECONDS)
    }
}
