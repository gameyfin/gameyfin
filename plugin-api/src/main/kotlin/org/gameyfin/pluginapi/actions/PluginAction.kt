package org.gameyfin.pluginapi.actions

/**
 * Represents an executable action provided by a plugin.
 *
 * Plugin actions allow plugins to expose functionality that can be triggered by the main application,
 * such as pre-generating torrent files, clearing caches, or performing maintenance tasks.
 *
 * @property id Unique identifier for the action within the plugin (e.g., "pre-generate-torrents")
 * @property name Human-readable name of the action (e.g., "Pre-generate Torrent Files")
 * @property description Optional description explaining what the action does
 * @property category Optional category for grouping related actions (e.g., "Maintenance", "Optimization")
 * @property handler The function that executes when this action is triggered
 */
data class PluginAction(
    val id: String,
    val name: String,
    val description: String? = null,
    val category: String? = null,
    val handler: () -> PluginActionResult
)
