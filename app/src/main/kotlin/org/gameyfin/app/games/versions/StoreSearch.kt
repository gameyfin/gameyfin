package org.gameyfin.app.games.versions

import org.springframework.stereotype.Service

/** A product found on a store, so that admins can pick a product instead of typing its ID. */
data class StoreSearchResultDto(
    val id: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?
)

@Service
class GameStoreSearchService(
    private val storeRegistry: GameStoreRegistry,
    private val thumbnailProxy: StoreThumbnailProxy
) {

    fun search(store: String, query: String): List<StoreSearchResultDto> {
        val active = storeRegistry.require(store)
        val trimmed = normalizeSearchQuery(query)
        if (trimmed.length < 2) return emptyList()
        return active.provider.search(trimmed.take(100))
            .take(MAX_SEARCH_RESULTS)
            .map { StoreSearchResultDto(it.id, it.title, it.subtitle, thumbnailProxy.rewrite(it.imageUrl)) }
    }

    companion object {
        private const val MAX_SEARCH_RESULTS = 10
    }
}

/**
 * Store searches are word based and some (Steam) return nothing when the query contains punctuation such as a dash
 * in "Wild Hunt — Remastered". Apostrophes are kept because they are part of words.
 */
internal fun normalizeSearchQuery(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim()
