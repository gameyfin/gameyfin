package de.grimsi.gameyfin.pluginapi.core.config

data class PluginConfigValidationResult(
    val result: PluginConfigValidationResultType,
    val errors: Map<String, String>? = null
) {
    companion object {
        val VALID = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)
        val UNKNOWN = PluginConfigValidationResult(PluginConfigValidationResultType.UNKNWOWN)
        fun INVALID(errors: Map<String, String>): PluginConfigValidationResult {
            return PluginConfigValidationResult(PluginConfigValidationResultType.INVALID, errors)
        }
    }

    fun isValid(): Boolean {
        return result == PluginConfigValidationResultType.VALID
    }
}

enum class PluginConfigValidationResultType {
    VALID,
    INVALID,
    UNKNWOWN,
}