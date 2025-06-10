package de.grimsi.gameyfinplugins.steamgriddb.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamGridDbSearchResult(
    val success: Boolean,
    val data: List<SteamGridDbGame>?
)

@Serializable
data class SteamGridDbGame(
    val id: Int,
    val name: String
)