package org.gameyfin.plugins.store.itch

import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.pf4j.Extension
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * itch.io, the store for indie games. The product ID is the game's itch.io address without the protocol, e.g.
 * `leafo.itch.io/x-moon`. itch.io has no public API for this, so search and downloads are read from public pages.
 */
@Suppress("Unused")
@Extension
class ItchProvider internal constructor(private val http: ItchHttp) : GameStoreProvider {

    // Required by PF4J, which instantiates extensions without arguments
    constructor() : this(JdkItchHttp)

    override val store = GameStoreInfo(
        id = "itch",
        displayName = "itch.io",
        productIdPattern = PRODUCT_ID_PATTERN,
        urlPatterns = listOf("(?i)https?://([a-z0-9][a-z0-9_-]*\\.itch\\.io/[a-z0-9][a-z0-9_-]*)"),
        caseInsensitiveIds = true
    )

    override fun search(query: String): List<GameStoreProduct> {
        val term = normalizeSearchQuery(query)
        if (term.length < 2) return emptyList()
        val encoded = URLEncoder.encode(term.take(100), StandardCharsets.UTF_8)
        return rankByTitle(parseItchSearch(http.getText("$BASE_URL/search?type=games&q=$encoded")), term)
    }

    override fun fetchVersions(productId: String): List<GameStoreVersion> {
        require(isValidProductId(productId)) { "Invalid itch.io game: '$productId'" }
        return parseItchUploads(http.getText("https://$productId"))
    }

    private companion object {
        const val BASE_URL = "https://itch.io"
    }
}
