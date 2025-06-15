package org.gameyfin.pluginapi.core.config

/**
 * Represents the result of plugin configuration validation.
 *
 * @property result The type of validation result (VALID, INVALID, UNKNOWN).
 * @property errors A map of configuration keys to error messages, present if validation failed.
 */
data class PluginConfigValidationResult(
    val result: PluginConfigValidationResultType,
    val errors: Map<String, String>? = null
) {
    companion object {
        /**
         * A valid configuration result with no errors.
         */
        val VALID = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        /**
         * An unknown configuration validation result.
         */
        val UNKNOWN = PluginConfigValidationResult(PluginConfigValidationResultType.UNKNWOWN)

        /**
         * Creates an invalid configuration result with the specified errors.
         *
         * @param errors A map of configuration keys to error messages.
         * @return An invalid PluginConfigValidationResult instance.
         */
        fun INVALID(errors: Map<String, String>): PluginConfigValidationResult {
            return PluginConfigValidationResult(PluginConfigValidationResultType.INVALID, errors)
        }
    }

    /**
     * Checks if the configuration is valid.
     *
     * @return True if the result is VALID, false otherwise.
     */
    fun isValid(): Boolean {
        return result == PluginConfigValidationResultType.VALID
    }
}

/**
 * Enum representing the possible types of plugin configuration validation results.
 */
enum class PluginConfigValidationResultType {
    VALID,
    INVALID,
    UNKNWOWN,
}