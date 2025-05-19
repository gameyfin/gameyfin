package de.grimsi.gameyfin.pluginapi.core

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
}

enum class PluginConfigValidationResultType {
    VALID,
    INVALID,
    UNKNWOWN,
}