package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.PluginClassLoader
import org.pf4j.PluginDescriptor
import org.pf4j.PluginManager

/**
 * Adds custom functionality to the [PluginClassLoader] for Gameyfin (mostly related to JAR signature validation).
 */
class GameyfinPluginClassLoader(
    pluginManager: PluginManager,
    pluginDescriptor: PluginDescriptor,
    parentClassLoader: ClassLoader,
) : PluginClassLoader(pluginManager, pluginDescriptor, parentClassLoader) {

    override fun loadClass(className: String?): Class<*>? {
        try {
            return super.loadClass(className)
        } catch (_: SecurityException) {
        }

        return null
    }
}