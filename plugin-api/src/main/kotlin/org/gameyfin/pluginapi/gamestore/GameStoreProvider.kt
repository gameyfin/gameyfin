package org.gameyfin.pluginapi.gamestore

import org.pf4j.ExtensionPoint
import java.time.Instant

/**
 * Extension point for game stores (e.g. Steam, GOG, Epic Games).
 *
 * A store provider lets Gameyfin look up products on a store, track the latest versions of a product and list its DLC.
 * It is independent of [org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider]: a plugin can implement both
 * (and users only have to install it once), or only this extension point.
 *
 * Implementations must only talk to the store they describe and must return versions ordered newest first.
 *
 * Typically one plugin wraps one data source. Several plugins can declare the same store (same [GameStoreInfo.id]),
 * e.g. an official API and a community archive. Gameyfin combines them: for search and versions the first source
 * (by plugin priority) that has results is used and the others are fallbacks, DLC and edition names from all sources
 * are merged without duplicates. A source should therefore return an empty list for what it does not provide.
 */
interface GameStoreProvider : ExtensionPoint {

    /**
     * Describes the store. Must be constant for the lifetime of the plugin.
     */
    val store: GameStoreInfo

    /**
     * Whether [search] is implemented. If no active source of a store supports search, the UI only offers entering
     * a product ID or URL manually.
     */
    val supportsSearch: Boolean get() = true

    /**
     * Searches the store's public catalog so that users can pick a product instead of typing its ID.
     *
     * @return Matching base games (not DLC), best matches first. Empty if the store has no catalog search.
     */
    fun search(query: String): List<GameStoreProduct> = emptyList()

    /**
     * Fetches the available versions (builds) of a product.
     *
     * @param productId A product ID in the format described by [GameStoreInfo.productIdPattern].
     * @return Versions ordered newest first. Empty if this source has no version data.
     * @throws Exception if the source cannot be reached or the product is unknown.
     */
    fun fetchVersions(productId: String): List<GameStoreVersion> = emptyList()

    /**
     * Lists the DLC and extras a store knows for a product.
     *
     * @param productId A product ID in the format described by [GameStoreInfo.productIdPattern].
     * @return The store's extras. Empty if the store has no such concept.
     */
    fun fetchDlc(productId: String): List<GameStoreDlc> = emptyList()

    /**
     * Suggests names of the editions (e.g. "Deluxe Edition") of a product, if the store lists them by name.
     * For stores on which editions are separate products, Gameyfin suggests the edition from the product title instead.
     *
     * @param productId A product ID in the format described by [GameStoreInfo.productIdPattern].
     * @return Edition names, or an empty list if unknown.
     */
    fun suggestEditions(productId: String): List<String> = emptyList()
}

/**
 * Describes a game store.
 *
 * @property id Stable, unique identifier of the store (e.g. "steam"). Links between games and stores are saved with this
 * ID, so it must never change. Lower case letters, digits and dashes only.
 * @property displayName Name shown to users (e.g. "Steam").
 * @property productIdPattern Regular expression a product ID must match completely.
 * @property urlPatterns Regular expressions for URLs of the store (or well-known sites about it) that contain a product ID.
 * The first capture group must be the product ID. Allows users to paste a URL instead of an ID.
 * @property caseInsensitiveIds Whether product IDs are case-insensitive. If true, Gameyfin lower-cases pasted IDs.
 */
data class GameStoreInfo(
    val id: String,
    val displayName: String,
    val productIdPattern: String,
    val urlPatterns: List<String> = emptyList(),
    val caseInsensitiveIds: Boolean = false
) {
    /**
     * Accepts a plain product ID or a URL matching one of [urlPatterns] and returns the normalized product ID,
     * or null if the input is not valid for this store.
     */
    fun parseProductId(input: String): String? {
        val trimmed = input.trim()
        val candidate = urlPatterns.firstNotNullOfOrNull {
            Regex(it).find(trimmed)?.groupValues?.getOrNull(1)
        } ?: trimmed
        val normalized = if (caseInsensitiveIds) candidate.lowercase() else candidate
        return normalized.takeIf { Regex(productIdPattern).matches(it) }
    }
}

/**
 * A product found on a store.
 *
 * @property id The store's identifier of the product.
 * @property title Product title.
 * @property subtitle Optional additional information (e.g. the publisher or the product type).
 * @property imageUrl Optional URL of a small thumbnail.
 */
data class GameStoreProduct(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null
)

/**
 * A version (build) of a store product.
 *
 * @property buildId The store's opaque identifier of the build. Must not be parsed as a version number.
 * @property branch The branch (e.g. "beta"), empty for the default branch.
 * @property label User-facing label of the version.
 * @property releasedAt When the build was released, if known.
 */
data class GameStoreVersion(
    val buildId: String,
    val branch: String = "",
    val label: String,
    val releasedAt: Instant? = null
)

/**
 * An extra (edition upgrade, DLC or bonus material) of a store product.
 *
 * @property id The store's identifier of the extra.
 */
data class GameStoreDlc(
    val id: String,
    val title: String,
    val kind: GameStoreDlcKind = GameStoreDlcKind.DLC
)

enum class GameStoreDlcKind {
    /** An edition that is offered as an upgrade of the base product instead of a separate product. */
    EDITION_UPGRADE,
    DLC,

    /** Soundtracks, art books and similar material, which stores sell as DLC. */
    BONUS
}
