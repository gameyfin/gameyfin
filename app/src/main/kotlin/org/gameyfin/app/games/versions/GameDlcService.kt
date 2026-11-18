package org.gameyfin.app.games.versions

import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/** An extra (edition upgrade, DLC or bonus material) of a store product. The ID is the store's own identifier. */
data class StoreDlcInfo(val dlcId: String, val title: String, val kind: DlcKind = DlcKind.DLC)

data class StoreDlcDto(val dlcId: String, val title: String, val kind: DlcKind = DlcKind.DLC)

data class IncludedDlcDto(val dlcId: String, val title: String, val detected: Boolean, val kind: DlcKind = DlcKind.DLC)

data class GameIncludedDlcDto(val linkId: Long, val store: String, val storeName: String, val storeAvailable: Boolean, val dlc: List<IncludedDlcDto>)

/**
 * Which DLC the installer(s) provided for a game include, per store link.
 * Candidates come from the store, the selection is stored by Gameyfin and independent of metadata plugins.
 */
@Service
class GameDlcService(
    private val gameRepository: GameRepository,
    private val linkRepository: GameStoreLinkRepository,
    private val dlcRepository: GameIncludedDlcRepository,
    private val storeRegistry: GameStoreRegistry
) {
    private val cache = ConcurrentHashMap<Pair<String, String>, CachedCandidates>()

    private class CachedCandidates(val fetchedAt: Long, val dlc: List<StoreDlcInfo>)

    @Transactional(readOnly = true)
    fun getIncluded(gameId: Long): List<GameIncludedDlcDto> {
        val links = linkRepository.findAllByGameId(gameId)
        if (links.isEmpty()) return emptyList()
        val activeStores = storeRegistry.activeStores().associateBy { it.id }
        val byLink = dlcRepository.findAllByLinkIdIn(links.map { it.id!! }).groupBy { it.linkId }
        return links.map { link ->
            GameIncludedDlcDto(
                linkId = link.id!!,
                store = link.store,
                storeName = activeStores[link.store]?.info?.displayName ?: link.store,
                storeAvailable = link.store in activeStores,
                dlc = byLink[link.id].orEmpty()
                    .sortedBy { it.title.lowercase() }
                    .map { IncludedDlcDto(it.dlcId, it.title, it.source == LinkSource.AUTO, it.kind) }
            )
        }
    }

    /** The DLC the store offers for a product, which can be picked as included. */
    fun getCandidates(store: String, storeAppId: String): List<StoreDlcDto> =
        candidates(store, storeAppId).map { StoreDlcDto(it.dlcId, it.title, it.kind) }

    /** Suggests the DLC that is present in the folder of the game, without saving anything. */
    fun detect(gameId: Long, store: String, storeAppId: String): List<StoreDlcDto> {
        val game = gameRepository.findByIdOrNull(gameId)
            ?: throw NoSuchElementException("Game $gameId not found")
        val candidates = candidates(store, storeAppId)
        if (candidates.isEmpty()) return emptyList()
        val names = collectNames(Path.of(game.metadata.path))
        return DlcInstallerMatcher.match(game.title.orEmpty(), names, candidates)
            .map { StoreDlcDto(it.dlcId, it.title, it.kind) }
    }

    /** Replaces the included DLC of a link. */
    @Transactional
    fun setIncluded(linkId: Long, dlc: List<IncludedDlcDto>): GameIncludedDlcDto {
        val link = linkRepository.findByIdOrNull(linkId)
            ?: throw NoSuchElementException("Store link $linkId not found")
        val activeStore = storeRegistry.require(link.store)
        require(dlc.size <= MAX_INCLUDED) { "Too many DLC entries (maximum $MAX_INCLUDED)" }

        val wanted = dlc.distinctBy { it.dlcId }
        wanted.forEach {
            require(CUSTOM_DLC_ID.matches(it.dlcId) || activeStore.isValidProductId(it.dlcId)) {
                "Invalid ${activeStore.info.displayName} DLC ID: '${it.dlcId}'"
            }
            require(it.title.isNotBlank() && it.title.length <= MAX_TITLE) { "Invalid DLC title for ${it.dlcId}" }
        }

        val existing = dlcRepository.findAllByLinkId(linkId).associateBy { it.dlcId }
        dlcRepository.deleteAll(existing.filterKeys { id -> wanted.none { it.dlcId == id } }.values)
        wanted.forEach { item ->
            val source = if (item.detected) LinkSource.AUTO else LinkSource.MANUAL
            val current = existing[item.dlcId]
            if (current == null) {
                dlcRepository.save(GameIncludedDlc(linkId = linkId, dlcId = item.dlcId, title = item.title.trim(), source = source, kind = item.kind))
            } else {
                current.title = item.title.trim()
                current.source = source
                current.kind = item.kind
            }
        }
        dlcRepository.flush()
        return getIncluded(link.gameId).first { it.linkId == linkId }
    }

    private fun candidates(store: String, storeAppId: String): List<StoreDlcInfo> {
        val (activeStore, id) = storeRegistry.parseProductId(store, storeAppId)
        val key = store to id
        val now = System.nanoTime()
        cache[key]?.takeIf { now - it.fetchedAt < CACHE_TTL.toNanos() }?.let { return it.dlc }
        return activeStore.provider.fetchDlc(id)
            .map { StoreDlcInfo(it.id, it.title, it.kind.toDlcKind()) }
            .also { cache[key] = CachedCandidates(now, it) }
    }

    private fun GameStoreDlcKind.toDlcKind() = when (this) {
        GameStoreDlcKind.EDITION_UPGRADE -> DlcKind.EDITION_UPGRADE
        GameStoreDlcKind.DLC -> DlcKind.DLC
        GameStoreDlcKind.BONUS -> DlcKind.BONUS
    }

    /** Names of files and folders below the game path, which is also where DLC installers are often placed. */
    private fun collectNames(gamePath: Path): List<String> {
        if (!Files.isDirectory(gamePath)) return listOfNotNull(gamePath.fileName?.toString())
        return Files.walk(gamePath, MAX_DEPTH).use { paths ->
            paths.filter { it != gamePath }.map { it.fileName.toString() }.limit(MAX_NAMES).toList()
        }
    }

    companion object {
        /** DLC the store does not list can be added by name. Their IDs are generated from the title. */
        private val CUSTOM_DLC_ID = Regex("^custom:[a-z0-9-]{1,100}$")
        private const val MAX_INCLUDED = 500
        private const val MAX_TITLE = 255
        private const val MAX_DEPTH = 4
        private const val MAX_NAMES = 5000L
        private val CACHE_TTL = Duration.ofMinutes(10)
    }
}
