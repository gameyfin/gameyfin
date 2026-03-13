package org.gameyfin.pluginapi.actions

/**
 * Represents the result of executing a plugin action.
 *
 * @property success Whether the action completed successfully
 * @property message Optional message providing details about the execution
 * @property data Optional data returned by the action (e.g., statistics, file paths)
 */
data class PluginActionResult(
    val success: Boolean,
    val message: String? = null,
    val data: Map<String, Any>? = null
) {
    companion object {
        /**
         * Creates a successful action result.
         *
         * @param message Optional success message
         * @param data Optional data to include
         * @return A successful [PluginActionResult]
         */
        fun success(message: String? = null, data: Map<String, Any>? = null): PluginActionResult {
            return PluginActionResult(success = true, message = message, data = data)
        }

        /**
         * Creates a failed action result.
         *
         * @param message Optional error message
         * @param data Optional data to include
         * @return A failed [PluginActionResult]
         */
        fun failure(message: String? = null, data: Map<String, Any>? = null): PluginActionResult {
            return PluginActionResult(success = false, message = message, data = data)
        }
    }
}

