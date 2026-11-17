package org.gameyfin.plugins.store.gogdb

import kotlinx.serialization.json.JsonElement
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.pf4j.Extension
import java.lang.System.Logger.Level
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * GOG builds and edition names from the GOGDB archive. Packs and editions (e.g. "Anniversary Edition") usually
 * have no builds of their own, so their versions are taken from the base game that GOGDB lists as included in
 * the pack. Provides nothing else for the GOG store.
 */
@Extension
class GogDbProvider(private val http: GogDbHttp = JdkGogDbHttp) : GameStoreProvider {

    private val log = System.getLogger(GogDbProvider::class.java.name)

    private class CachedEditions(val fetchedAt: Long, val names: List<String>)

    private val editionCache = ConcurrentHashMap<String, CachedEditions>()

    override val supportsSearch = false

    override val store = GameStoreInfo(
        id = "gog",
        displayName = "GOG",
        productIdPattern = "^\\d{1,20}$",
        urlPatterns = listOf("gogdb\\.org/product/(\\d{1,20})")
    )

    override fun fetchVersions(productId: String): List<GameStoreVersion> {
        require(GOG_ID.matches(productId)) { "Invalid GOG product ID: '$productId'" }

        try {
            return fetchProductVersions(productId)
        } catch (ownFailure: Exception) {
            val baseGameVersions = fetchBaseGameVersions(productId)
            if (baseGameVersions != null) return baseGameVersions
            throw ownFailure
        }
    }

    override fun suggestEditions(productId: String): List<String> {
        val id = store.parseProductId(productId) ?: return emptyList()
        val now = System.nanoTime()
        editionCache[id]?.takeIf { now - it.fetchedAt < CACHE_TTL.toNanos() }?.let { return it.names }
        return try {
            parseGogDbEditions(getGogDbProduct(id)).also { editionCache[id] = CachedEditions(now, it) }
        } catch (e: Exception) {
            log.log(Level.DEBUG, "Could not look up the editions of GOG product $id", e)
            emptyList()
        }
    }

    private fun fetchProductVersions(productId: String): List<GameStoreVersion> {
        val versions = parseGogDbProduct(getGogDbProduct(productId))
        check(versions.isNotEmpty()) { "GOGDB returned no usable Windows generation 2 builds for product $productId" }
        return versions.sortedByNewest()
    }

    /** Returns null if the product is not a pack, or none of the games it includes has builds. */
    private fun fetchBaseGameVersions(productId: String): List<GameStoreVersion>? {
        val includedIds = try {
            parseGogDbIncludedGameIds(getGogDbProduct(productId))
        } catch (e: Exception) {
            log.log(Level.DEBUG, "Could not look up the games included in GOG product $productId", e)
            return null
        }

        for (includedId in includedIds.take(MAX_INCLUDED_PRODUCTS)) {
            try {
                if (!isGogDbGame(getGogDbProduct(includedId))) continue
                val versions = fetchProductVersions(includedId)
                log.log(Level.INFO, "GOG product $productId has no builds of its own; using base game $includedId")
                return versions
            } catch (e: Exception) {
                log.log(Level.DEBUG, "GOG product $includedId, included in $productId, has no usable builds", e)
            }
        }
        return null
    }

    private fun getGogDbProduct(productId: String): JsonElement {
        require(GOG_ID.matches(productId)) { "Invalid GOG product ID: '$productId'" }
        return http.getJson(URI.create("$GOGDB_URL/data/products/$productId/product.json"))
    }

    private companion object {
        const val GOGDB_URL = "https://www.gogdb.org"
        const val MAX_INCLUDED_PRODUCTS = 10
        val CACHE_TTL: Duration = Duration.ofMinutes(10)
    }
}
