package org.gameyfin.app.core.plugins.dto

import com.fasterxml.jackson.annotation.JsonInclude
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.pf4j.PluginState

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PluginUpdateDto(
    val id: String,
    val state: PluginState? = null,
    val config: Map<String, String?>? = null,
    val configValidation: PluginConfigValidationResult? = null,
    val priority: Int? = null,
    /**
     * Full plugin data, sent if a plugin has been installed or replaced (e.g. updated via the marketplace)
     */
    val plugin: PluginDto? = null,
    /**
     * True if the plugin has been removed
     */
    val removed: Boolean? = null,
)
