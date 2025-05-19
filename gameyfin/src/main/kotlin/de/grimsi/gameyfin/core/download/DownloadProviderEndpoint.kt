package de.grimsi.gameyfin.core.download

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll

@Endpoint
@PermitAll
class DownloadProviderEndpoint(
    private val downloadService: DownloadService
) {
    fun getProviders(): List<DownloadProviderDto> {
        return downloadService.getProviders()
    }
}