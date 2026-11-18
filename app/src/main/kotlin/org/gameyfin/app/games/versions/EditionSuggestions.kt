package org.gameyfin.app.games.versions

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/**
 * Suggests edition names for a store product, if the store's plugin lists the editions of a product by name.
 * For stores on which editions are separate products, the label is suggested from the product title instead.
 */
@Service
class EditionSuggestionService(private val storeRegistry: GameStoreRegistry) {

    fun suggest(store: String, storeAppId: String): List<String> {
        val active = storeRegistry.find(store) ?: return emptyList()
        val id = active.parseProductId(storeAppId) ?: return emptyList()
        return try {
            active.provider.suggestEditions(id)
                .map { it.trim() }
                .filter {
                    it.isNotEmpty() && it.length <= GameStoreLinkService.MAX_EDITION_LENGTH &&
                            !GameStoreLinkService.STANDARD_EDITION.matches(it)
                }
                .distinct()
                .take(MAX_SUGGESTIONS)
        } catch (e: Exception) {
            log.debug(e) { "Could not look up the editions of ${active.info.displayName} product $id" }
            emptyList()
        }
    }

    companion object {
        private const val MAX_SUGGESTIONS = 20
    }
}
