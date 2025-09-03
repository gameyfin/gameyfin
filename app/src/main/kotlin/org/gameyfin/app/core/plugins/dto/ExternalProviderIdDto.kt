package org.gameyfin.app.core.plugins.dto

class ExternalProviderIdDto(
    val pluginId: String,
    val externalProviderId: String,
) {
    override fun toString(): String {
        return "$pluginId:$externalProviderId"
    }
}