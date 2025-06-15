package org.gameyfin.plugins.metadata.steamgriddb.dto

import kotlinx.serialization.Serializable

@Serializable
data class SteamGridDbSearchResult(
    val success: Boolean,
    val data: List<SteamGridDbGame>?
)