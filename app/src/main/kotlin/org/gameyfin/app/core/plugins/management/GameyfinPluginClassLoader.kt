package org.gameyfin.app.core.plugins.management

import org.pf4j.ClassLoadingStrategy
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
    classLoadingStrategy: ClassLoadingStrategy,
) : PluginClassLoader(pluginManager, pluginDescriptor, parentClassLoader, classLoadingStrategy) {

    override fun loadClass(className: String?): Class<*>? {
        try {
            return super.loadClass(className)
        } catch (_: SecurityException) {
            // This can happen when the plugin JAR is signed but the signature is invalid (e.g. due to file corruption or tampering).
            // In this case, we want to catch the exception and return null to indicate that the class could not be loaded, instead of crashing the entire plugin loading process.
        }

        return null
    }
}