package org.gameyfin.app.core.plugins.management

import org.pf4j.DevelopmentPluginLoader
import org.pf4j.PluginClassLoader
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import java.nio.file.Path

/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
class GameyfinDevelopmentPluginLoader(
    pluginManager: PluginManager,
    private val parentClassLoader: ClassLoader
) : DevelopmentPluginLoader(pluginManager) {

    override fun createPluginClassLoader(pluginPath: Path, pluginDescriptor: PluginDescriptor): PluginClassLoader {
        return PluginClassLoader(pluginManager, pluginDescriptor, parentClassLoader)
    }
}