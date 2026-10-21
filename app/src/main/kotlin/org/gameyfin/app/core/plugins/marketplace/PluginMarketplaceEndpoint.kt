package org.gameyfin.app.core.plugins.marketplace

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.plugins.marketplace.dto.MarketplacePluginDto
import org.gameyfin.app.core.plugins.marketplace.dto.PluginMarketplaceCreateDto
import org.gameyfin.app.core.plugins.marketplace.dto.PluginMarketplaceDto
import org.gameyfin.app.core.plugins.marketplace.dto.PluginMarketplaceUpdateDto

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginMarketplaceEndpoint(
    private val pluginMarketplaceService: PluginMarketplaceService
) {

    fun getMarketplaces(): List<PluginMarketplaceDto> = pluginMarketplaceService.getMarketplaces()

    fun addMarketplace(marketplace: PluginMarketplaceCreateDto): PluginMarketplaceDto =
        pluginMarketplaceService.addMarketplace(marketplace)

    fun updateMarketplace(id: String, update: PluginMarketplaceUpdateDto): PluginMarketplaceDto =
        pluginMarketplaceService.updateMarketplace(id, update)

    fun removeMarketplace(id: String) = pluginMarketplaceService.removeMarketplace(id)

    fun getPlugins(): List<MarketplacePluginDto> = pluginMarketplaceService.getPlugins()

    fun refresh(): List<MarketplacePluginDto> = pluginMarketplaceService.refresh()

    fun install(marketplaceId: String, pluginId: String, version: String?): PluginDto? =
        pluginMarketplaceService.install(marketplaceId, pluginId, version)

    fun update(pluginId: String, version: String?): PluginDto? = pluginMarketplaceService.update(pluginId, version)

    fun uninstall(pluginId: String): PluginDto? = pluginMarketplaceService.uninstall(pluginId)
}
