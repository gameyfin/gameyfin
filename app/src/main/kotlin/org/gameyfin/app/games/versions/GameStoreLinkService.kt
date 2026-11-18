package org.gameyfin.app.games.versions

import org.gameyfin.app.games.repositories.GameRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages which store products a game is linked to. Links are only created and removed by admins.
 * New links require an active game store plugin for the store; existing links are kept when it is disabled.
 */
@Service
class GameStoreLinkService(
    private val linkRepository: GameStoreLinkRepository,
    private val gameRepository: GameRepository,
    private val storeRegistry: GameStoreRegistry
) {
    companion object {
        const val MAX_EDITION_LENGTH = 100
        val STANDARD_EDITION = Regex("(standard|base|default|regular)( (edition|game|version))?", RegexOption.IGNORE_CASE)

        // Prefer stores that provide real version names over stores that only provide build IDs
        private val TRACKING_PREFERENCE = listOf("gog", "epic", "steam")
    }

    fun getLinks(gameId: Long): List<GameStoreLink> = linkRepository.findAllByGameId(gameId)

    /** [storeAppId] may be a plain product ID or the URL of a product page. */
    @Transactional
    fun addLink(gameId: Long, store: String, storeAppId: String): GameStoreLink {
        val id = storeRegistry.parseProductId(store, storeAppId).second
        if (!gameRepository.existsById(gameId)) throw NoSuchElementException("Game $gameId not found")

        val link = linkRepository.findByGameIdAndStoreAndStoreAppId(gameId, store, id)
            ?: linkRepository.save(GameStoreLink(gameId = gameId, store = store, storeAppId = id))
        ensureTrackedLink(gameId)
        return link
    }

    @Transactional
    fun removeLink(linkId: Long) {
        val link = linkRepository.findById(linkId).orElse(null) ?: return
        linkRepository.delete(link)
        linkRepository.flush()
        ensureTrackedLink(link.gameId)
    }

    @Transactional
    fun setTracked(linkId: Long) {
        val link = linkRepository.findById(linkId).orElseThrow { NoSuchElementException("Link $linkId not found") }
        linkRepository.findAllByGameId(link.gameId).forEach { it.tracked = it.id == link.id }
    }

    @Transactional
    fun setEdition(linkId: Long, edition: String?) {
        val link = linkRepository.findById(linkId).orElseThrow { NoSuchElementException("Link $linkId not found") }
        // The standard/base edition is the base product and does not get a label
        val cleaned = edition?.trim()?.takeIf { it.isNotEmpty() && !STANDARD_EDITION.matches(it) }
        require(cleaned == null || cleaned.length <= MAX_EDITION_LENGTH) {
            "Edition must be at most $MAX_EDITION_LENGTH characters"
        }
        link.edition = cleaned
    }

    /** Makes sure exactly one link of the game is tracked (if it has any). */
    private fun ensureTrackedLink(gameId: Long) {
        val links = linkRepository.findAllByGameId(gameId)
        if (links.isEmpty() || links.count { it.tracked } == 1) return

        val tracked = links.firstOrNull { it.tracked }
            ?: links.sortedWith(compareBy({ TRACKING_PREFERENCE.indexOf(it.store) }, { it.id })).first()
        links.forEach { it.tracked = it === tracked }
        linkRepository.saveAll(links)
    }
}
