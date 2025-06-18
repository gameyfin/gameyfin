package org.gameyfin.plugins.metadata.steamgriddb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.gameyfin.plugins.metadata.steamgriddb.util.InstantEpochSecondsSerializer
import java.time.Instant


@Serializable
data class SteamGridDbGame(
    val id: Int,
    val name: String,
    @SerialName("release_date")
    @Serializable(with = InstantEpochSecondsSerializer::class)
    val releaseDate: Instant? = null
)