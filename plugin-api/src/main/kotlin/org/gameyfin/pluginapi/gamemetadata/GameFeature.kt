package org.gameyfin.pluginapi.gamemetadata

/**
 * Enum representing the features of a game.
 */
enum class GameFeature(
    val displayName: String
) {
    SINGLEPLAYER("Singleplayer"),
    MULTIPLAYER("Multiplayer"),
    CO_OP("Co-op"),
    CROSS_PLATFORM("Cross-Platform"),
    MODDING("Modding"),
    VR("VR"),
    AR("AR"),
    CLOUD_SAVES("Cloud Saves"),
    CLOUD_PLAY("Cloud Play"),
    ACHIEVEMENTS("Achievements"),
    LEADERBOARDS("Leaderboards"),
    WORKSHOP("Workshop"),
    CONTROLLER_SUPPORT("Controller Support"),
    REMOTE_PLAY("Remote Play"),
    LOCAL_MULTIPLAYER("Local Multiplayer"),
    LOCAL_CO_OP("Local Co-op"),
    ONLINE_MULTIPLAYER("Online Multiplayer"),
    ONLINE_CO_OP("Online Co-op"),
    ONLINE_PVP("Online PvP"),
    ONLINE_PVE("Online PvE"),
    LOCAL_PVP("Local PvP"),
    LOCAL_PVE("Local PvE"),
    CROSSPLAY("Crossplay"),
    SPLITSCREEN("Splitscreen")
}