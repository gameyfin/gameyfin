package org.gameyfin.app.core.plugins.marketplace.dto

import org.gameyfin.app.core.plugins.management.PluginOrigin
import java.time.Instant

data class PluginMarketplaceDto(
    val id: String,
    val name: String,
    val url: String,
    val indexUrl: String,
    val enabled: Boolean,
    val official: Boolean,
    val pluginCount: Int,
    val lastFetchedAt: Instant? = null,
    val lastError: String? = null,
)

data class PluginMarketplaceCreateDto(
    val name: String,
    val url: String,
)

data class PluginMarketplaceUpdateDto(
    val name: String? = null,
    val enabled: Boolean? = null,
)

enum class MarketplacePluginStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
    INCOMPATIBLE,
}

data class MarketplacePluginReleaseDto(
    val version: String,
    val date: Instant? = null,
    val requires: String? = null,
    val compatible: Boolean,
    val changelog: String? = null,
)

data class MarketplacePluginDto(
    val id: String,
    val marketplaceId: String,
    val marketplaceName: String,
    val officialMarketplace: Boolean,
    val name: String,
    val shortDescription: String? = null,
    val description: String? = null,
    val author: String? = null,
    val license: String? = null,
    val url: String? = null,
    val hasLogo: Boolean,
    val types: List<String>,
    val tags: List<String>,
    /**
     * Releases sorted by version (newest first)
     */
    val releases: List<MarketplacePluginReleaseDto>,
    val latestVersion: String? = null,
    val latestCompatibleVersion: String? = null,
    val installedVersion: String? = null,
    val installedOrigin: PluginOrigin? = null,
    /**
     * ID of the marketplace the installed version was installed from
     */
    val installedFromMarketplaceId: String? = null,
    val status: MarketplacePluginStatus,
)
