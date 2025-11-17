package org.gameyfin.app.core.download.provider

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.download.files.DownloadService

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