package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.AbstractPluginManager
import org.pf4j.CompoundPluginDescriptorFinder
import org.pf4j.CompoundPluginLoader
import org.pf4j.CompoundPluginRepository
import org.pf4j.DefaultExtensionFactory
import org.pf4j.DefaultExtensionFinder
import org.pf4j.DefaultPluginFactory
import org.pf4j.DefaultPluginLoader
import org.pf4j.DefaultPluginRepository
import org.pf4j.DefaultVersionManager
import org.pf4j.DevelopmentPluginRepository
import org.pf4j.ExtensionFactory
import org.pf4j.ExtensionFinder
import org.pf4j.JarPluginLoader
import org.pf4j.JarPluginRepository
import org.pf4j.ManifestPluginDescriptorFinder
import org.pf4j.PluginDescriptorFinder
import org.pf4j.PluginFactory
import org.pf4j.PluginLoader
import org.pf4j.PluginRepository
import org.pf4j.PluginStatusProvider
import org.pf4j.PluginWrapper
import org.pf4j.PropertiesPluginDescriptorFinder
import org.pf4j.VersionManager
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
@Component
class GameyfinPluginManager(
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginStatusProvider: DatabasePluginStatusProvider
) : AbstractPluginManager() {

    private val log = KotlinLogging.logger {}

    override fun createPluginRepository(): PluginRepository {
        return CompoundPluginRepository()
            .add(DevelopmentPluginRepository(pluginsRoots), this::isDevelopment)
            .add(JarPluginRepository(pluginsRoots), this::isNotDevelopment)
            .add(DefaultPluginRepository(pluginsRoots), this::isNotDevelopment)
    }

    override fun createPluginFactory(): PluginFactory {
        return DefaultPluginFactory()
    }

    override fun createExtensionFactory(): ExtensionFactory {
        return DefaultExtensionFactory()
    }

    override fun createPluginDescriptorFinder(): PluginDescriptorFinder {
        return CompoundPluginDescriptorFinder()
            .add(PropertiesPluginDescriptorFinder())
            .add(ManifestPluginDescriptorFinder())
    }

    override fun createExtensionFinder(): ExtensionFinder {
        val extensionFinder = DefaultExtensionFinder(this)
        addPluginStateListener(extensionFinder)
        return extensionFinder
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

    override fun createVersionManager(): VersionManager? {
        return DefaultVersionManager()
    }

    override fun createPluginStatusProvider(): PluginStatusProvider {
        return pluginStatusProvider
    }

    override fun loadPluginFromPath(pluginPath: Path?): PluginWrapper? {
        val pluginWrapper = super.loadPluginFromPath(pluginPath)

        // Inject config after loading, before starting
        if (pluginWrapper != null) {
            configurePlugin(pluginWrapper)
        }

        return pluginWrapper
    }

    fun restart(pluginId: String) {
        val plugin = getPlugin(pluginId)?.plugin ?: return
        plugin.stop()
        (plugin as GameyfinPlugin).loadConfig(getConfig(pluginId))
        plugin.start()
    }

    private fun configurePlugin(pluginWrapper: PluginWrapper) {
        val plugin = pluginWrapper.plugin
        if (plugin is GameyfinPlugin) {
            val config = getConfig(pluginWrapper.pluginId)
            plugin.loadConfig(config)
        }
    }

    private fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigRepository.findAllById_PluginId(pluginId).map { it.id.key to it.value }.toMap()
    }
}