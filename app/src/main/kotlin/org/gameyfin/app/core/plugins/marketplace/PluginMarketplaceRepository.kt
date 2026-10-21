package org.gameyfin.app.core.plugins.marketplace

import org.springframework.data.jpa.repository.JpaRepository

interface PluginMarketplaceRepository : JpaRepository<PluginMarketplaceEntry, String> {
    fun existsByIndexUrl(indexUrl: String): Boolean
}
