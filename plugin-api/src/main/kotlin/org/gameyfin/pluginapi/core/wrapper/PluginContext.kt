package org.gameyfin.pluginapi.core.wrapper

import org.gameyfin.pluginapi.data.GameyfinDataService
import java.nio.file.Path

/**
 * Runtime context of a plugin, provided by Gameyfin when the plugin is instantiated.
 *
 * It is the plugin's single entry point to everything Gameyfin offers to plugins.
 * Plugins receive it through their constructor, see [GameyfinPlugin].
 */
interface PluginContext {

    /**
     * The ID of the plugin (as declared in its manifest).
     */
    val pluginId: String

    /**
     * The version of the plugin (as declared in its manifest).
     */
    val pluginVersion: String

    /**
     * Directory for storing plugin-specific files. It is not guaranteed to exist.
     */
    val dataDirectory: Path

    /**
     * Read-only access to games, libraries and collections of Gameyfin.
     */
    val dataService: GameyfinDataService
}
