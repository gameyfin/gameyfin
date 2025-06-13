package de.grimsi.gameyfinplugins.steamgriddb.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamGridDbGameResult(
    val success: Boolean,
    val data: SteamGridDbGame?
)