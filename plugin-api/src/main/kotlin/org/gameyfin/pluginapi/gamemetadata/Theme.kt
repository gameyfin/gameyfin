package org.gameyfin.pluginapi.gamemetadata

/**
 * Enum representing the themes of a game.
 */
enum class Theme(
    val displayName: String
) {
    UNKNOWN("Unknown"),
    ACTION("Action"),
    FANTASY("Fantasy"),
    SCIENCE_FICTION("Science Fiction"),
    HORROR("Horror"),
    THRILLER("Thriller"),
    SURVIVAL("Survival"),
    HISTORICAL("Historical"),
    STEALTH("Stealth"),
    COMEDY("Comedy"),
    BUSINESS("Business"),
    DRAMA("Drama"),
    NON_FICTION("Non-Fiction"),
    SANDBOX("Sandbox"),
    EDUCATIONAL("Educational"),
    KIDS("Kids"),
    OPEN_WORLD("Open World"),
    WARFARE("Warfare"),
    PARTY("Party"),
    FOUR_X("4X"),
    MYSTERY("Mystery"),
    EROTIC("Erotic"),
    ROMANCE("Romance")
}