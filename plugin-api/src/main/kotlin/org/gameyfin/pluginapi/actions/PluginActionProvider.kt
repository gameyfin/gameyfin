package org.gameyfin.pluginapi.actions

import org.pf4j.ExtensionPoint

/**
 * Extension point for providing executable actions from plugins.
 *
 * Plugins can implement this interface to expose actions that can be listed and triggered
 * from the main application. This is useful for maintenance tasks, optimizations, or any
 * functionality that should be executable on-demand.
 *
 * Example use cases:
 * - Pre-generating torrent files
 * - Clearing caches
 * - Refreshing metadata
 * - Running diagnostics
 */
interface PluginActionProvider : ExtensionPoint {

    /**
     * Returns the list of actions provided by this plugin.
     *
     * The list can be static or dynamic based on plugin state.
     *
     * @return A list of [PluginAction] objects that can be executed
     */
    fun getActions(): List<PluginAction>

    /**
     * Executes a specific action by its ID.
     *
     * @param actionId The unique identifier of the action to execute
     * @return The [PluginActionResult] indicating success or failure
     * @throws IllegalArgumentException if the action ID is not found
     */
    fun executeAction(actionId: String): PluginActionResult {
        val action = getActions().find { it.id == actionId }
            ?: throw IllegalArgumentException("Action with ID '$actionId' not found")

        return action.handler()
    }
}

