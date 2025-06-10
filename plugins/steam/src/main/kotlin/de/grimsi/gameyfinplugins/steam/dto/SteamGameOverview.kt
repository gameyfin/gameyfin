package de.grimsi.gameyfinplugins.steam.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamSearchResult(
    val total: Int,
    val items: List<SteamGame>
)

@Serializable
data class SteamGame(
    val type: String,
    val name: String,
    val id: Int
)