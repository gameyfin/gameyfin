package de.grimsi.gameyfin.pluginapi.gamemetadata

import java.net.URL
import java.time.Instant

class GameMetadata(
    val title: String,
    val description: String,
    val coverUrl: URL,
    val release: Instant,
    val userRating: Int?,
    val criticRating: Int?,
    val developedBy: Set<String>,
    val publishedBy: Set<String>,
    val genres: Set<Genre>,
    val themes: Set<Theme>,
    val keywords: Set<String>,
    val screenshotUrls: Set<URL>,
    val videoUrls: Set<URL>,
    val features: Set<GameFeature>,
    val perspectives: Set<PlayerPerspective>
)

enum class Genre {
    UNKNOWN,
    PINBALL,
    ADVENTURE,
    INDIE,
    ARCADE,
    VISUAL_NOVEL,
    CARD_AND_BOARD_GAME,
    MOBA,
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