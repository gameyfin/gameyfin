package org.gameyfin.pluginapi.gamemetadata

import java.net.URI
import java.time.Instant

/**
 * Represents metadata for a game, including identifiers, descriptive information, media, ratings, and categorization.
 *
 * @property originalId The unique identifier for the game from the original source.
 * @property title The title of the game.
 * @property description A description of the game, or null if not available.
 * @property coverUrls List of URIs to the game's cover images, or null if not available.
 * @property headerUrls List of URIs to the game's header images, or null if not available.
 * @property release The release date and time of the game, or null if not available.
 * @property userRating The user rating for the game, or null if not available.
 * @property criticRating The critic rating for the game, or null if not available.
 * @property developedBy The set of developer names, or null if not available.
 * @property publishedBy The set of publisher names, or null if not available.
 * @property genres The set of genres associated with the game, or null if not available.
 * @property themes The set of themes associated with the game, or null if not available.
 * @property keywords The set of keywords associated with the game, or null if not available.
 * @property screenshotUrls The set of URIs to screenshots, or null if not available.
 * @property videoUrls The set of URIs to videos, or null if not available.
 * @property features The set of features associated with the game, or null if not available.
 * @property perspectives The set of player perspectives, or null if not available.
 */
data class GameMetadata(
    val originalId: String,
    val title: String,
    val description: String? = null,
    val coverUrls: List<URI>? = null,
    val headerUrls: List<URI>? = null,
    val release: Instant? = null,
    val userRating: Int? = null,
    val criticRating: Int? = null,
    val developedBy: Set<String>? = null,
    val publishedBy: Set<String>? = null,
    val genres: Set<Genre>? = null,
    val themes: Set<Theme>? = null,
    val keywords: Set<String>? = null,
    val screenshotUrls: Set<URI>? = null,
    val videoUrls: Set<URI>? = null,
    val features: Set<GameFeature>? = null,
    val perspectives: Set<PlayerPerspective>? = null
)

/**
 * Enum representing the genre of a game.
 */
enum class Genre {
    UNKNOWN,
    ACTION,
    PINBALL,
    ADVENTURE,
    INDIE,
    ARCADE,
    VISUAL_NOVEL,
    CARD_AND_BOARD_GAME,
    MOBA,
    MMO,
    POINT_AND_CLICK,
    FIGHTING,
    SHOOTER,
    MUSIC,
    PLATFORM,
    PUZZLE,
    RACING,
    REAL_TIME_STRATEGY,
    ROLE_PLAYING,
    SIMULATOR,
    SPORT,
    STRATEGY,
    TURN_BASED_STRATEGY,
    TACTICAL,
    HACK_AND_SLASH_BEAT_EM_UP,
    QUIZ_TRIVIA
}

/**
 * Enum representing the theme of a game.
 */
enum class Theme {
    UNKNOWN,
    ACTION,
    FANTASY,
    SCIENCE_FICTION,
    HORROR,
    THRILLER,
    SURVIVAL,
    HISTORICAL,
    STEALTH,
    COMEDY,
    BUSINESS,
    DRAMA,
    NON_FICTION,
    SANDBOX,
    EDUCATIONAL,
    KIDS,
    OPEN_WORLD,
    WARFARE,
    PARTY,
    FOUR_X,
    MYSTERY,
    EROTIC,
    ROMANCE
}

enum class GameFeature {
    SINGLEPLAYER,
    MULTIPLAYER,
    CO_OP,
    CROSS_PLATFORM,
    MODDING,
    VR,
    AR,
    CLOUD_SAVES,
    CLOUD_PLAY,
    ACHIEVEMENTS,
    LEADERBOARDS,
    WORKSHOP,
    CONTROLLER_SUPPORT,
    REMOTE_PLAY,
    LOCAL_MULTIPLAYER,
    LOCAL_CO_OP,
    ONLINE_MULTIPLAYER,
    ONLINE_CO_OP,
    ONLINE_PVP,
    ONLINE_PVE,
    LOCAL_PVP,
    LOCAL_PVE,
    CROSSPLAY,
    SPLITSCREEN
}

enum class PlayerPerspective {
    UNKNOWN,
    FIRST_PERSON,
    THIRD_PERSON,
    BIRD_VIEW_ISOMETRIC,
    SIDE_VIEW,
    TEXT,
    AUDITORY,
    VIRTUAL_REALITY
}