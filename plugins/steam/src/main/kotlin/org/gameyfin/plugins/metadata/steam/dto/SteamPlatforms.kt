package org.gameyfin.plugins.metadata.steam.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamPlatforms(
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean
)