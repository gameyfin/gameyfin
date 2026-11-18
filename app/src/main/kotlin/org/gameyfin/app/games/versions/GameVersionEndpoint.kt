package org.gameyfin.app.games.versions

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@AnonymousAllowed
class GameVersionEndpoint(
    private val statusService: GameVersionStatusService,
    private val refreshTask: VersionRefreshTask,
    private val previewService: StoreVersionPreviewService
) {

    fun getStatus(gameId: Long): List<GameStoreVersionStatusDto> = statusService.getStatus(gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun setInstalledVersion(linkId: Long, versionId: Long?, detected: Boolean): GameStoreVersionStatusDto =
        statusService.setInstalledVersion(linkId, versionId, if (detected) LinkSource.AUTO else LinkSource.MANUAL)

    @RolesAllowed(Role.Names.ADMIN)
    fun previewDetection(linkId: Long): VersionDetectionResultDto = statusService.previewDetection(linkId)

    @RolesAllowed(Role.Names.ADMIN)
    fun detectInstalledVersion(linkId: Long): VersionDetectionResultDto =
        statusService.detectInstalledVersion(linkId)

    @RolesAllowed(Role.Names.ADMIN)
    fun refreshVersions(linkId: Long) = refreshTask.refreshLink(linkId)

    @RolesAllowed(Role.Names.ADMIN)
    fun previewVersions(gameId: Long, store: String, storeAppId: String): StoreVersionPreviewDto =
        previewService.preview(gameId, store, storeAppId)
}
