package org.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

/**
 * Extension point for providing game metadata.
 *
 * Implementations of this interface are responsible for fetching game metadata by title or by unique identifier.
 * This is typically used to allow plugins to provide metadata for games from various sources.
 */
interface GameMetadataProvider : ExtensionPoint {

    /**
     * Returns the set of platforms supported by this metadata provider.
     *
     * @return A set of [Platform] enums indicating supported platforms.
     */
    val supportedPlatforms: Set<Platform>

    /**
     * Fetches a list of game metadata entries matching the given game title.
     *
     * @param gameTitle The title of the game to search for.
     * @param platformFilter A set of [Platform] enums to filter the search results. Only games available on these platforms should be returned.
     * @param maxResults The maximum number of results to return. Defaults to 1.
     * @return A list of [GameMetadata] objects matching the title, or an empty list if none found.
     */
    fun fetchByTitle(gameTitle: String, platformFilter: Set<Platform>, maxResults: Int = 1): List<GameMetadata>

    /**
     * Fetches game metadata by its unique identifier.
     *
     * @param id The unique identifier of the game.
     * @return The [GameMetadata] for the given id, or null if not found.
     */
    fun fetchById(id: String): GameMetadata?
}