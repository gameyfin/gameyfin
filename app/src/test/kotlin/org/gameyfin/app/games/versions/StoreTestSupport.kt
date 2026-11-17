package org.gameyfin.app.games.versions

import io.mockk.mockk
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProvider

internal val STEAM_INFO = GameStoreInfo(
    id = "steam",
    displayName = "Steam",
    productIdPattern = "^\\d{1,20}$",
    urlPatterns = listOf("store\\.steampowered\\.com/app/(\\d{1,20})", "steamdb\\.info/app/(\\d{1,20})")
)

internal val GOG_INFO = GameStoreInfo(
    id = "gog",
    displayName = "GOG",
    productIdPattern = "^\\d{1,20}$",
    urlPatterns = listOf("gogdb\\.org/product/(\\d{1,20})")
)

internal val EPIC_INFO = GameStoreInfo(
    id = "epic",
    displayName = "Epic Games",
    productIdPattern = "^[0-9a-f]{32}$",
    urlPatterns = listOf("egdata\\.app/offers/([0-9a-fA-F]{32})"),
    caseInsensitiveIds = true
)

internal fun activeStore(
    info: GameStoreInfo,
    provider: GameStoreProvider = mockk(relaxed = true),
    priority: Int = 0
) = ActiveGameStore(info, provider, "plugin.${info.id}", priority)

/** A registry that offers exactly the given stores, as if their plugins were enabled. */
internal fun registryOf(vararg stores: ActiveGameStore): GameStoreRegistry =
    object : GameStoreRegistry(mockk(), mockk()) {
        override fun activeStores() = stores.toList()
    }
