package org.gameyfin.app.games.versions

import org.springframework.stereotype.Service
import java.time.Instant

data class StoreVersionPreviewDto(
    val versions: List<StoreVersionInfoDto>,
    /** Index into [versions] of the version that matches the installed files, if one was detected. */
    val detectedIndex: Int?
)

data class StoreVersionInfoDto(
    val buildId: String,
    val branch: String,
    val label: String,
    val releasedAt: Instant?
)

/**
 * Looks up the versions of a store product that is not linked to a game (yet), so that an admin can pick
 * the installed version in the same dialog in which the store is added. Nothing is saved.
 */
@Service
class StoreVersionPreviewService(
    private val storeRegistry: GameStoreRegistry,
    private val statusService: GameVersionStatusService
) {

    fun preview(gameId: Long, store: String, storeAppId: String): StoreVersionPreviewDto {
        val (activeStore, id) = storeRegistry.parseProductId(store, storeAppId)

        val versions = activeStore.provider.fetchVersions(id)
        val detected = try {
            matchDetectedVersion(statusService.detectInstalledValues(gameId, store, id), versions) {
                Triple(it.buildId, it.branch, it.label)
            }
        } catch (e: NoSuchElementException) {
            throw e
        } catch (e: Exception) {
            null
        }

        return StoreVersionPreviewDto(
            versions = versions.map { StoreVersionInfoDto(it.buildId, it.branch, it.label, it.releasedAt) },
            detectedIndex = detected?.let { versions.indexOf(it) }
        )
    }
}
