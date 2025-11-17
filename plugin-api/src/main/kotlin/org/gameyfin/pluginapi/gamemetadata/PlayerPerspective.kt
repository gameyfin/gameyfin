package org.gameyfin.pluginapi.gamemetadata

/**
 * Enum representing the perspectives of a game.
 */
enum class PlayerPerspective(
    val displayName: String
) {
    UNKNOWN("Unknown"),
    FIRST_PERSON("First-Person"),
    THIRD_PERSON("Third-Person"),
    BIRD_VIEW_ISOMETRIC("Bird View/Isometric"),
    SIDE_VIEW("Side View"),
    TEXT("Text"),
    AUDITORY("Auditory"),
    VIRTUAL_REALITY("Virtual Reality")
}