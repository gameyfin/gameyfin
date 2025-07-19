package org.gameyfin.app.core.plugins.management

import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.ExtensionDescriptor
import org.pf4j.ExtensionWrapper
import org.pf4j.LegacyExtensionFinder
import org.pf4j.PluginManager

class GameyfinExtensionFinder(pluginManager: PluginManager) : LegacyExtensionFinder(pluginManager) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    override fun find(pluginId: String?): MutableList<ExtensionWrapper<*>?> {
        log.debug { "Finding extensions from plugin '$pluginId'" }
        val result: MutableList<ExtensionWrapper<*>?> = ArrayList()

        val classNames = findClassNames(pluginId)
        if (classNames.isEmpty()) {
            return result
        }

        if (pluginId != null) {
            log.trace { "Checking extensions from plugin '$pluginId'" }
        } else {
            log.trace { "Checking extensions from classpath" }
        }

        val classLoader =
            if (pluginId != null) pluginManager.getPluginClassLoader(pluginId) else javaClass.getClassLoader()

        for (className in classNames) {
            try {
                log.debug { "Loading class '$className' using class loader '$classLoader'" }
                val extensionClass = classLoader.loadClass(className)

                val extensionWrapper: ExtensionWrapper<*> = createExtensionWrapper(extensionClass)
                result.add(extensionWrapper)
                log.debug { "Added extension '$className' with ordinal ${extensionWrapper.ordinal}" }
            } catch (e: ClassNotFoundException) {
                log.error { "Error loading plugin: ${e.message}" }
                log.debug(e) {}
            } catch (e: NoClassDefFoundError) {
                log.error { "Error loading plugin: ${e.message}" }
                log.debug(e) {}
            }
        }

        if (result.isEmpty()) {
            log.debug { "No extensions found for plugin '$pluginId'" }
        } else {
            log.debug { "Found ${result.size} extensions for plugin '$pluginId'" }
        }

        return result
    }

    private fun createExtensionWrapper(extensionClass: Class<*>): ExtensionWrapper<*> {
        val extensionAnnotation = findExtensionAnnotation(extensionClass)
        val ordinal = extensionAnnotation?.ordinal ?: 0
        val descriptor = ExtensionDescriptor(ordinal, extensionClass)

        return ExtensionWrapper<Any?>(descriptor, pluginManager.extensionFactory)
    }
}