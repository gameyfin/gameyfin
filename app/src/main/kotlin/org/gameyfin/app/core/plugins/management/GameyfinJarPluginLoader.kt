package org.gameyfin.app.core.plugins.management

import org.pf4j.DevelopmentPluginLoader
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager
import org.pf4j.util.FileUtils
import java.nio.file.Files
import java.nio.file.Path

/**
 * JAR plugin loader using a [GameyfinPluginClassLoader]
 */
class GameyfinJarPluginLoader(
    pluginManager: PluginManager
) : DevelopmentPluginLoader(pluginManager) {

    override fun isApplicable(pluginPath: Path): Boolean {
        return Files.exists(pluginPath) && FileUtils.isJarFile(pluginPath)
    }

    override fun loadPlugin(pluginPath: Path, pluginDescriptor: PluginDescriptor?): ClassLoader {
        if (pluginDescriptor == null) {
            throw IllegalArgumentException("Plugin descriptor cannot be null")
        }

        val pluginClassLoader = GameyfinPluginClassLoader(pluginManager, pluginDescriptor, javaClass.getClassLoader())
        pluginClassLoader.addFile(pluginPath.toFile())

        return pluginClassLoader
    }
}