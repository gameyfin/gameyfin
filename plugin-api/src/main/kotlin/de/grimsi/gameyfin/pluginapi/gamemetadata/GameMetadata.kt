package de.grimsi.gameyfin.pluginapi.gamemetadata
import java.net.URL
import java.time.Instant

class GameMetadata(
    val title: String,
    val description: String,
    val release: Instant,
    val userRating: Int?,
    val criticRating: Int?,
    val developedBy: List<String>,
    val publishedBy: List<String>,
    val genres: List<Genre>,
    val themes: List<Theme>,
    val screenshotUrls: List<URL>,
    val videoUrls: List<URL>,
    val features: List<GameFeature>,
    val perspectives: List<PlayerPerspective>
)

enum class Genre {
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
    CROSSPLAY
}

enum class PlayerPerspective {
    FIRST_PERSON,
    THIRD_PERSON,
    BIRD_VIEW_ISOMETRIC,
    SIDE_VIEW,
    TEXT,
    AUDITORY,
    VIRTUAL_REALITY
}