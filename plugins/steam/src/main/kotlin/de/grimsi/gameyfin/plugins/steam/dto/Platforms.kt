package de.grimsi.gameyfin.plugins.steam.dto

import kotlinx.serialization.Serializable

@Serializable
data class Platforms(
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean
)