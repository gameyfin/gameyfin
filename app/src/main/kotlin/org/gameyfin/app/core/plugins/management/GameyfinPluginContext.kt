package org.gameyfin.app.core.plugins.management

import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.data.GameyfinDataService
import java.nio.file.Path

/**
 * [PluginContext] implementation handed to every Gameyfin plugin by the [GameyfinPluginFactory].
 *
 * The [dataService] is resolved lazily on first access, so creating a context never depends on the state of the
 * Spring application context.
 */
class GameyfinPluginContext(
    override val pluginId: String,
    override val pluginVersion: String,
    override val dataDirectory: Path,
    dataServiceProvider: () -> GameyfinDataService
) : PluginContext {

    companion object {
        private const val PLUGIN_DATA_ROOT = "plugindata"

        fun dataDirectoryOf(pluginId: String): Path = Path.of(PLUGIN_DATA_ROOT, pluginId)
    }

    override val dataService: GameyfinDataService by lazy(dataServiceProvider)
}
