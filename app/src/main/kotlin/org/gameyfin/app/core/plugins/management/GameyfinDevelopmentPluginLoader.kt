package org.gameyfin.app.core.plugins.management

import org.pf4j.*
import java.nio.file.Path

/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
class GameyfinDevelopmentPluginLoader(
    pluginManager: PluginManager,
    private val parentClassLoader: ClassLoader
) : DevelopmentPluginLoader(pluginManager) {

    override fun createPluginClassLoader(pluginPath: Path, pluginDescriptor: PluginDescriptor): PluginClassLoader {
        return GameyfinPluginClassLoader(pluginManager, pluginDescriptor, parentClassLoader, ClassLoadingStrategy.APD)
    }
}