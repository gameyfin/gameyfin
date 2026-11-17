package org.gameyfin.plugins.store.steam

import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.pf4j.Extension
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Steam as a game store: catalog search and DLC from the official store.steampowered.com endpoints.
 * Builds are provided by the separate SteamCMD plugin.
 */
@Suppress("Unused")
@Extension
class SteamStoreProvider internal constructor(private val http: SteamStoreHttp) : GameStoreProvider {

    // Required by PF4J, which instantiates extensions without arguments
    constructor() : this(JdkSteamStoreHttp)

    override val store = GameStoreInfo(
        id = "steam",
        displayName = "Steam",
        productIdPattern = "^\\d{1,20}$",
        urlPatterns = listOf(
            "store\\.steampowered\\.com/app/(\\d{1,20})",
            "steamdb\\.info/app/(\\d{1,20})"
        )
    )

    override fun search(query: String): List<GameStoreProduct> {
        val term = URLEncoder.encode(normalizeSearchQuery(query), StandardCharsets.UTF_8)
        val results = parseSteamSearch(http.getJson("$SEARCH_URL?term=$term&cc=us&l=en"))
        return keepBaseGames(results) { id -> appType(id) }
    }

    /** The search lists DLC and soundtracks as plain apps, so each hit's type is looked up. Null if unknown. */
    private fun appType(id: String): String? = runCatching {
        parseSteamAppType(http.getJson("$DETAILS_URL?appids=$id&filters=basic"), id)
    }.getOrNull()

    /**
     * Steam lists the DLC app IDs on the app page, the names require one request per DLC.
     * The number of DLC is capped to stay well below Steam's rate limits.
     */
    override fun fetchDlc(productId: String): List<GameStoreDlc> {
        require(isValidSteamId(productId)) { "Invalid Steam app ID: '$productId'" }
        val ids = parseSteamDlcIds(appDetails(productId), productId).take(MAX_DLC)
        return ids.map { id ->
            val details = runCatching { appDetails(id) }.getOrNull()
            val title = details?.let { parseSteamAppName(it, id) }
            GameStoreDlc(
                id,
                title ?: "Steam app $id",
                classifyExtra(title.orEmpty(), isBonus = details?.let { parseSteamAppType(it, id) } == "music")
            )
        }
    }

    private fun appDetails(appId: String) = http.getJson("$DETAILS_URL?appids=$appId&cc=us&l=english")

    companion object {
        private const val SEARCH_URL = "https://store.steampowered.com/api/storesearch"
        private const val DETAILS_URL = "https://store.steampowered.com/api/appdetails"
        private const val MAX_DLC = 80
    }
}
