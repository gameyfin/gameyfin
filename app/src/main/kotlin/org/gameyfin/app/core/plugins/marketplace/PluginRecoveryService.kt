package org.gameyfin.app.core.plugins.marketplace

import jakarta.annotation.PostConstruct
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.notifications.NotificationStore
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.management.PluginsDiscoveredEvent
import org.gameyfin.app.core.tasks.*
import org.gameyfin.pluginapi.notifications.NotificationRequest
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service

@Service
class PluginRecoveryService(
    private val candidates: PluginRecoveryRepository,
    private val installer: GameyfinUpdateManager,
    private val plugins: PluginService,
    private val registry: TaskRegistry,
    private val runs: TaskRunService,
    private val notifications: NotificationStore
) {
    companion object {
        const val TASK = "core.plugin.recover"
        private val log = KotlinLogging.logger {}
    }
    @Volatile private var discovered = false

    @PostConstruct
    fun register() {
        registry.register(TaskDefinition(
            key = TASK, name = "Recover formerly bundled plugins",
            description = "One-time marketplace migration. Recovers missing legacy plugins automatically and resumes pending work on startup.",
            schedulable = false, manuallyTriggerable = false, cancellable = false,
            visibleInTaskList = false,
            action = TaskAction.Run { context ->
                check(discovered) { "Local plugin discovery has not finished yet" }
                val failures = mutableListOf<String>()
                installer.getMarketplaceRepository(PluginMarketplaceService.OFFICIAL_MARKETPLACE_ID)?.refresh()
                candidates.findAllByCompletedFalse().forEach { candidate ->
                    context.progress("Recovering ${candidate.pluginId}")
                    try {
                        installer.recoverMissingPlugin(candidate.pluginId)
                        plugins.emitPlugin(candidate.pluginId)
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to recover ${candidate.pluginId}" }
                        failures += candidate.pluginId
                        context.progress("Recovery failed for ${candidate.pluginId}: ${e.message}")
                    }
                }
                notifications.resolve("core:plugin-recovery")
                if (failures.isNotEmpty()) {
                    notifications.publish("core:plugin-recovery", NotificationRequest(
                        "Plugin recovery needs attention",
                        "Could not recover: ${failures.joinToString()}. Pending plugins will be retried automatically on the next start.",
                        NotificationSeverity.WARNING
                    ))
                    error("Failed to recover ${failures.joinToString()}; pending plugins will retry on the next start")
                }
                TaskResult("Plugin recovery complete")
            }
        ))
    }

    @EventListener
    fun afterDiscovery(event: PluginsDiscoveredEvent) {
        discovered = true
        if (candidates.findAllByCompletedFalse().isNotEmpty()) runs.trigger(TASK, TaskTrigger.SYSTEM)
    }
}
