package de.grimsi.gameyfin.plugins.steam.dto

import kotlinx.serialization.SerialName
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
    val id: Int,
    val price: Price,
    @SerialName("tiny_image") val tinyImage: String,
    val metascore: String,
    val platforms: Platforms,
    @SerialName("streamingvideo") val streamingVideo: Boolean,
    @SerialName("controller_support") val controllerSupport: String
)

@Serializable
data class Price(
    val currency: String,
    val initial: Int,
    val final: Int
)