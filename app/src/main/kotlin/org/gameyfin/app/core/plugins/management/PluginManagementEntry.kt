package org.gameyfin.app.core.plugins.management

import jakarta.persistence.Entity
import jakarta.persistence.Id

@Entity
data class PluginManagementEntry(
    @Id
    val pluginId: String,

    var enabled: Boolean = false,

    var priority: Int = 0,

    var trustLevel: PluginTrustLevel = PluginTrustLevel.UNKNOWN,

    /**
     * ID of the marketplace the plugin was installed from (null if the plugin was not installed via a marketplace)
     */
    var sourceMarketplaceId: String? = null,
)