package org.gameyfin.app.games.versions

import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class StoreVersionCacheService(
    private val linkRepository: GameStoreLinkRepository,
    private val versionRepository: StoreVersionRepository,
    private val stateRepository: GameVersionStateRepository
) {

    @Transactional
    fun update(linkId: Long, versions: List<GameStoreVersion>, checkedAt: Instant = Instant.now()) {
        val link = linkRepository.findByIdOrNull(linkId)
            ?: throw NoSuchElementException("Store link $linkId not found")
        val existing = versionRepository.findAllByLinkId(linkId)
            .associateBy { it.buildId to it.branch }
            .toMutableMap()
        val retained = mutableSetOf<Pair<String, String>>()

        versions.distinctBy { it.buildId to it.branch }.forEach { version ->
            require(version.buildId.isNotBlank()) { "Store version build ID must not be blank" }
            require(version.buildId.length <= 255) { "Store version build ID exceeds 255 characters" }
            require(version.branch.length <= 255) { "Store version branch exceeds 255 characters" }
            require(version.label.isNotBlank()) { "Store version label must not be blank" }
            require(version.label.length <= 255) { "Store version label exceeds 255 characters" }
            val key = version.buildId to version.branch
            retained += key
            val current = existing[key]
            if (current == null) {
                versionRepository.save(
                    StoreVersion(
                        linkId = linkId,
                        buildId = version.buildId,
                        branch = version.branch,
                        label = version.label,
                        releasedAt = version.releasedAt
                    )
                )
            } else {
                current.label = version.label
                current.releasedAt = version.releasedAt
            }
        }

        val installedVersionId = stateRepository.findByIdOrNull(linkId)?.installedVersionId
        versionRepository.deleteAll(
            existing.filterKeys { it !in retained }
                .values
                .filterNot { it.id == installedVersionId }
        )
        link.versionsCheckedAt = checkedAt
    }
}
