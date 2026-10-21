package org.gameyfin.app.core.plugins.dto

import org.gameyfin.app.core.plugins.management.PluginOrigin
import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.pf4j.PluginState

data class PluginDto(
    val id: String,
    val types: List<String>,
    val name: String,
    val description: String,
    val shortDescription: String? = null,
    val version: String,
    val author: String,
    val license: String? = null,
    val url: String? = null,
    val hasLogo: Boolean,
    val state: PluginState,
    val configMetadata: List<PluginConfigMetadataDto>? = null,
    val config: Map<String, String?>? = null,
    val configValidation: PluginConfigValidationResult? = null,
    val priority: Int,
    val trustLevel: PluginTrustLevel,
    /**
     * Whether the plugin is a local plugin (bundled or added manually) or has been installed via a marketplace
     */
    val origin: PluginOrigin = PluginOrigin.LOCAL,
    /**
     * ID of the marketplace the plugin was installed from
     */
    val sourceMarketplaceId: String? = null,
    /**
     * Gameyfin version constraint of the plugin ("Plugin-Requires" in the manifest)
     */
    val requires: String? = null,
    /**
     * Whether the plugin is compatible with this Gameyfin version
     */
    val compatible: Boolean = true,
    /**
     * Reason why the plugin failed to load/start (if any)
     */
    val failureReason: String? = null,
)