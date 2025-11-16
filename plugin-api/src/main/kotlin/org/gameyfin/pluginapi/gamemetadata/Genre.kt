package org.gameyfin.pluginapi.gamemetadata

/**
 * Enum representing the genres of a game.
 */
enum class Genre(
    val displayName: String
) {
    UNKNOWN("Unknown"),
    ACTION("Action"),
    PINBALL("Pinball"),
    ADVENTURE("Adventure"),
    INDIE("Indie"),
    ARCADE("Arcade"),
    VISUAL_NOVEL("Visual Novel"),
    CARD_AND_BOARD_GAME("Card & Board Game"),
    MOBA("MOBA"),
    MMO("MMO"),
    POINT_AND_CLICK("Point-and-Click"),
    FIGHTING("Fighting"),
    SHOOTER("Shooter"),
    MUSIC("Music"),
    PLATFORM("Platform"),
    PUZZLE("Puzzle"),
    RACING("Racing"),
    REAL_TIME_STRATEGY("Real-Time Strategy"),
    ROLE_PLAYING("Role-Playing"),
    SIMULATOR("Simulator"),
    SPORT("Sport"),
    STRATEGY("Strategy"),
    TURN_BASED_STRATEGY("Turn-Based Strategy"),
    TACTICAL("Tactical"),
    HACK_AND_SLASH_BEAT_EM_UP("Hack and Slash/Beat 'em up"),
    QUIZ_TRIVIA("Quiz/Trivia")
}