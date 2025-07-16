package org.gameyfin.app.core.download

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import org.gameyfin.app.core.annotations.DynamicPublicAccess

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class DownloadProviderEndpoint(
    private val downloadService: DownloadService
) {
    fun getProviders(): List<DownloadProviderDto> {
        return downloadService.getProviders().sortedByDescending { it.priority }
    }
}