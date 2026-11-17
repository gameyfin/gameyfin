package org.gameyfin.plugins.store.steamcmd

import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.pf4j.Extension

/**
 * Steam branch builds via the unofficial steamcmd.net API, because Steam only exposes build IDs and no
 * user-facing versions. Provides nothing else for the Steam store.
 */
@Suppress("Unused")
@Extension
class SteamCmdProvider internal constructor(private val http: SteamCmdHttp) : GameStoreProvider {

    // Required by PF4J, which instantiates extensions without arguments
    constructor() : this(JdkSteamCmdHttp)

    override val supportsSearch = false

    override val store = GameStoreInfo(
        id = "steam",
        displayName = "Steam",
        productIdPattern = "^\\d{1,20}$",
        urlPatterns = listOf(
            "store\\.steampowered\\.com/app/(\\d{1,20})",
            "steamdb\\.info/app/(\\d{1,20})"
        )
    )

    override fun fetchVersions(productId: String): List<GameStoreVersion> {
        require(isValidSteamId(productId)) { "Invalid Steam app ID: '$productId'" }
        return parseSteamVersions(http.getJson("$VERSIONS_URL/$productId"), productId)
    }

    private companion object {
        const val VERSIONS_URL = "https://api.steamcmd.net/v1/info"
    }
}
