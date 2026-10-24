package org.gameyfin.app.core.plugins.marketplace

import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.tasks.*
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.pf4j.PluginState
import org.springframework.stereotype.Service

@Service
class PluginInstallationTaskService(
    private val registry: TaskRegistry,
    private val runService: TaskRunService,
    private val marketplaceService: PluginMarketplaceService
) {
    companion object {
        const val INSTALL = "core.plugin.install"
        const val UPDATE = "core.plugin.update"
        const val UNINSTALL = "core.plugin.uninstall"
    }

    @PostConstruct
    fun registerTasks() {
        listOf(INSTALL to "Install plugin", UPDATE to "Update plugin", UNINSTALL to "Uninstall plugin")
            .forEach { (key, name) ->
                registry.register(TaskDefinition(
                    key = key,
                    name = name,
                    schedulable = false,
                    manuallyTriggerable = false,
                    cancellable = false,
                    visibleInOverview = false,
                    action = TaskAction.Dispatch { error("Plugin operations require a plugin ID") }
                ))
            }
    }

    fun install(marketplaceId: String, pluginId: String, version: String?): TaskRunDto =
        runService.start(INSTALL, TaskTrigger.MANUAL, displayName = "Install plugin $pluginId") { context ->
            context.progress("Installing plugin $pluginId")
            installationResult(marketplaceService.install(marketplaceId, pluginId, version), pluginId)
        }

    fun update(pluginId: String, version: String?): TaskRunDto =
        runService.start(UPDATE, TaskTrigger.MANUAL, displayName = "Update plugin $pluginId") { context ->
            context.progress("Updating plugin $pluginId")
            installationResult(marketplaceService.update(pluginId, version), pluginId)
        }

    fun uninstall(pluginId: String): TaskRunDto =
        runService.start(UNINSTALL, TaskTrigger.MANUAL, displayName = "Uninstall plugin $pluginId") { context ->
            context.progress("Uninstalling plugin $pluginId")
            val fallback = marketplaceService.uninstall(pluginId)
            TaskResult(if (fallback == null) "Plugin $pluginId uninstalled"
            else "Plugin $pluginId reverted to local version ${fallback.version}. ${stateSummary(fallback)}")
        }

    private fun installationResult(plugin: PluginDto?, pluginId: String): TaskResult =
        TaskResult(if (plugin == null) "Plugin $pluginId installed"
        else "${plugin.name} version ${plugin.version} installed. ${stateSummary(plugin)}")

    private fun stateSummary(plugin: PluginDto): String = when (plugin.state) {
        PluginState.STARTED -> "Plugin is active."
        PluginState.DISABLED -> "Plugin is disabled; review and enable it in the Installed tab."
        else -> "Plugin could not be started" + (plugin.failureReason?.let { ": $it" } ?: ".")
    }
}
