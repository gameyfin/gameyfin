package org.gameyfin.plugins.metadata.steamgriddb.dto

import kotlinx.serialization.Serializable


@Serializable
data class SteamGridDbGame(
    val id: Int,
    val name: String
)