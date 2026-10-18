package org.gameyfin.app.core.plugins.management

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.pf4j.DefaultPluginFactory
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import java.lang.reflect.InvocationTargetException

/**
 * Instantiates plugins using a constructor with a single [PluginContext] parameter (see
 * [org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin]).
 *
 * Plugin classes without such a constructor are instantiated by the [DefaultPluginFactory]
 * (i.e. using a `PluginWrapper` or no-arg constructor).
 */
class GameyfinPluginFactory(
    private val contextFactory: (PluginWrapper) -> PluginContext
) : DefaultPluginFactory() {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    override fun createInstance(pluginClass: Class<*>, pluginWrapper: PluginWrapper): Plugin? {
        val constructor = try {
            pluginClass.getConstructor(PluginContext::class.java)
        } catch (_: NoSuchMethodException) {
            return super.createInstance(pluginClass, pluginWrapper)
        }

        return try {
            constructor.newInstance(contextFactory(pluginWrapper)) as Plugin
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            log.error(cause) { "Failed to instantiate plugin '${pluginWrapper.pluginId}': ${cause.message}" }
            null
        }
    }
}
