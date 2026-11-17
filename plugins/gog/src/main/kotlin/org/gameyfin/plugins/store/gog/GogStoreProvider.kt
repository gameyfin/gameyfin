package org.gameyfin.plugins.store.gog

import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.pf4j.Extension
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** GOG's official endpoints only: catalog search, content-system builds and DLC. Builds also come from GOGDB via a separate plugin. */
@Extension
class GogStoreProvider(private val http: GogHttp = JdkGogHttp) : GameStoreProvider {

    override val store = GameStoreInfo(
        id = "gog",
        displayName = "GOG",
        productIdPattern = "^\\d{1,20}$",
        urlPatterns = listOf("gogdb\\.org/product/(\\d{1,20})")
    )

    override fun search(query: String): List<GameStoreProduct> {
        val normalized = normalizeSearchQuery(query)
        if (normalized.length < 2) return emptyList()
        val term = normalized.take(100)
        val filter = encode("like:$term")
        val uri = URI.create("$CATALOG_URL?query=$filter&productType=${encode("in:game,pack")}&limit=$MAX_SEARCH_RESULTS")
        return filterByTitleRelevance(term, parseGogSearch(http.getJson(uri))).take(MAX_SEARCH_RESULTS)
    }

    override fun fetchVersions(productId: String): List<GameStoreVersion> {
        require(GOG_ID.matches(productId)) { "Invalid GOG product ID: '$productId'" }

        val uri = URI.create("$CONTENT_SYSTEM_URL/products/$productId/os/windows/builds?generation=2")
        val page = parseGogBuildPage(http.getJson(uri))
        check(page.itemCount == page.totalCount) {
            "GOG returned ${page.itemCount} builds out of ${page.totalCount} for product $productId"
        }
        return page.versions.sortedByNewest()
    }

    override fun fetchDlc(productId: String): List<GameStoreDlc> {
        require(GOG_ID.matches(productId)) { "Invalid GOG product ID: '$productId'" }
        return parseGogDlc(http.getJson(URI.create("$API_URL/$productId?expand=expanded_dlcs")))
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val CONTENT_SYSTEM_URL = "https://content-system.gog.com"
        const val API_URL = "https://api.gog.com/products"
        const val CATALOG_URL = "https://catalog.gog.com/v1/catalog"
    }
}
