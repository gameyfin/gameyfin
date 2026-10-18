package org.gameyfin.app.core.tasks

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.pluginapi.tasks.GameyfinTask
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers the tasks provided by plugins (see [GameyfinTask]) while the plugins are started.
 */
@Service
class PluginTaskRegistrar(
    private val pluginManager: GameyfinPluginManager,
    private val registry: TaskRegistry,
    private val runService: TaskRunService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        fun taskKey(pluginId: String, key: String) = "plugin.$pluginId.$key"
    }

    /** Keys of the registered tasks per plugin */
    private val registeredTasks = ConcurrentHashMap<String, List<String>>()

    @PostConstruct
    fun init() {
        pluginManager.addPluginStateListener { onPluginStateChanged(it) }
        pluginManager.startedPlugins.forEach { register(it.pluginId) }
    }

    private fun onPluginStateChanged(event: PluginStateEvent) {
        val pluginId = event.plugin?.pluginId ?: return
        if (event.pluginState == PluginState.STARTED) register(pluginId) else unregister(pluginId)
    }

    @Synchronized
    private fun register(pluginId: String) {
        unregister(pluginId)
        val tasks = try {
            pluginManager.getExtensions(GameyfinTask::class.java, pluginId).toList()
        } catch (e: Throwable) {
            log.warn { "Could not load the tasks of plugin $pluginId: ${e.message}" }
            return
        }
        if (tasks.isEmpty()) return

        val pluginName = pluginName(pluginId)
        val keys = tasks.mapNotNull { task ->
            try {
                val key = taskKey(pluginId, task.key)
                registry.register(
                    TaskDefinition(
                        key = key,
                        name = task.name,
                        description = task.description,
                        source = TaskSource.PLUGIN,
                        pluginId = pluginId,
                        defaultSchedule = task.defaultSchedule,
                        scheduledByDefault = task.scheduledByDefault,
                        cancellable = task.cancellable,
                        settings = task.settings,
                        parameters = task.parameters,
                        action = TaskAction.Run { context -> task.execute(context) }
                    )
                )
                key
            } catch (e: Throwable) {
                log.warn { "Could not register task of plugin $pluginName: ${e.message}" }
                null
            }
        }
        registeredTasks[pluginId] = keys
        log.info { "Registered ${keys.size} task(s) of plugin $pluginName" }
    }

    fun pluginName(pluginId: String): String =
        (pluginManager.getPlugin(pluginId)?.descriptor as? GameyfinPluginDescriptor)?.pluginName ?: pluginId

    @Synchronized
    private fun unregister(pluginId: String) {
        registeredTasks.remove(pluginId)?.forEach {
            registry.unregister(it)
            runService.cancelAll(it)
        }
    }
}
