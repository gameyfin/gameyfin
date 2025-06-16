package org.gameyfin.plugins.metadata.steamgriddb.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamGridDbHeroResult(
    val success: Boolean,
    val data: List<SteamGridDbHero>?
)

@Serializable
data class SteamGridDbHero(
    val id: Int,
    val url: String
)