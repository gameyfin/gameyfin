package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.*
import org.springframework.stereotype.Component
import java.nio.file.Path
import kotlin.io.path.Path

/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
@Component
class GameyfinPluginManager(
    val pluginConfigRepository: PluginConfigRepository,
    val dbPluginStatusProvider: DatabasePluginStatusProvider
) : DefaultPluginManager(Path(System.getProperty("pf4j.pluginsDir", "plugins"))) {

    private val log = KotlinLogging.logger {}

    // This took me way too long to figure out...
    // But I learned a lot about Kotlin and Java interoperability in the process
    init {
        pluginStatusProvider = dbPluginStatusProvider

        pluginStateListeners.add { event ->
            if (event is PluginStateEvent) {
                log.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
                if (event.oldState == PluginState.DISABLED) {
                    startPlugin(event.plugin.pluginId)
                } else if (event.pluginState == PluginState.DISABLED) {
                    stopPlugin(event.plugin.pluginId)
                }
            }
        }
    }

    override fun createPluginLoader(): PluginLoader {
        val compoundPluginLoader = CompoundPluginLoader()
        val developmentPluginLoader = GameyfinPluginLoader(this, javaClass.classLoader)
        val jarPluginLoader = JarPluginLoader(this)
        val defaultPluginLoader = DefaultPluginLoader(this)

        return compoundPluginLoader
            .add(developmentPluginLoader, this::isDevelopment)
            .add(jarPluginLoader, this::isNotDevelopment)
            .add(defaultPluginLoader, this::isNotDevelopment)
    }

    override fun createPluginStatusProvider(): PluginStatusProvider {
        return dbPluginStatusProvider
    }

    override fun loadPluginFromPath(pluginPath: Path?): PluginWrapper? {
        val pluginWrapper = super.loadPluginFromPath(pluginPath)

        // Inject config after loading, before starting
        if (pluginWrapper != null) {
            configurePlugin(pluginWrapper)
        }

        return pluginWrapper
    }

    override fun startPlugin(pluginId: String?): PluginState? {
        if(pluginId == null)  return PluginState.FAILED

        // Validate config before starting the plugin
        if (!validatePluginConfig(pluginId)) {
            log.error { "Plugin $pluginId has invalid configuration" }

            val pluginWrapper = getPlugin(pluginId)
            pluginWrapper.pluginState = PluginState.FAILED
            this.firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginWrapper.pluginState));
            return pluginWrapper.pluginState
        }

        return super.startPlugin(pluginId)
    }

    override fun startPlugins() {
        for (pluginWrapper in resolvedPlugins) {
            val pluginState = pluginWrapper.pluginState
            if (!pluginState.isDisabled && !pluginState.isStarted) {

                // Validate config before starting the plugin
                if (!validatePluginConfig(pluginWrapper.pluginId)) {
                    log.error { "Plugin ${pluginWrapper.pluginId} has invalid configuration" }
                    pluginWrapper.pluginState = PluginState.FAILED

                    firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginState))
                    return
                }

                try {
                    log.info { "${"Start plugin '{}'"} ${getPluginLabel(pluginWrapper.descriptor)}"}
                    pluginWrapper.plugin.start()
                    pluginWrapper.pluginState = PluginState.STARTED
                    pluginWrapper.failedException = null
                    startedPlugins.add(pluginWrapper)
                } catch (e: LinkageError) {
                    pluginWrapper.pluginState = PluginState.FAILED
                    pluginWrapper.failedException = e
                    log.error { "${"Unable to start plugin '{}'"} ${getPluginLabel(pluginWrapper.descriptor)} $e"}
                } catch (e: Exception) {
                    pluginWrapper.pluginState = PluginState.FAILED
                    pluginWrapper.failedException = e
                    log.error { "${"Unable to start plugin '{}'"} ${getPluginLabel(pluginWrapper.descriptor)} $e"}
                } finally {
                    firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginState))
                }
            }
        }
    }

    fun restart(pluginId: String) {
        val plugin = getPlugin(pluginId)?.plugin ?: return
        stopPlugin(pluginId)
        (plugin as GameyfinPlugin).loadConfig(getConfig(pluginId))
        startPlugin(pluginId)
    }

    fun validatePluginConfig(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId)?.plugin ?: return false
        if (plugin is GameyfinPlugin) {
            return plugin.validateConfig()
        }
        return false
    }

    private fun configurePlugin(pluginWrapper: PluginWrapper) {
        val plugin = pluginWrapper.plugin
        if (plugin is GameyfinPlugin) {
            val config = getConfig(pluginWrapper.pluginId)
            plugin.loadConfig(config)
        }
    }

    private fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigRepository.findAllById_PluginId(pluginId).associate { it.id.key to it.value }
    }
}