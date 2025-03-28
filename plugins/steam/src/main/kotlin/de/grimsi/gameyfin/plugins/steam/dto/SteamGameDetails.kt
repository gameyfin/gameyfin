package de.grimsi.gameyfin.plugins.steam.dto

import de.grimsi.gameyfin.plugins.steam.util.SteamDateSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SteamDetailsResultWrapper(
    val success: Boolean,
    val data: SteamGameDetails
)

@Serializable
data class SteamGameDetails(
    val name: String,
    @SerialName("detailed_description") val detailedDescription: String,
    @SerialName("header_image") val headerImage: String,
    val developers: List<String>,
    val publishers: List<String>,
    val categories: List<Category>,
    val genres: List<SteamGenre>,
    val screenshots: List<Screenshot>,
    val movies: List<Movie>,
    @SerialName("release_date") val releaseDate: ReleaseDate? = null
)

@Serializable
data class Category(
    val id: Int? = null,
    val description: String? = null
)

@Serializable
data class SteamGenre(
    val id: Int? = null,
    val description: String? = null
)

@Serializable
data class Screenshot(
    val id: Int,
    @SerialName("path_thumbnail") val pathThumbnail: String? = null,
    @SerialName("path_full") val pathFull: String? = null
)

@Serializable
data class Movie(
    val id: Int? = null,
    val name: String? = null,
    val thumbnail: String? = null,
    val webm: Webm? = null,
    val mp4: Mp4? = null,
    val highlight: Boolean? = null
)

@Serializable
data class Webm(
    val `480`: String? = null,
    val max: String? = null
)

@Serializable
data class Mp4(
    val `480`: String? = null,
    val max: String? = null
)

@Serializable
data class ReleaseDate(
    @SerialName("coming_soon") val comingSoon: Boolean? = null,
    @Serializable(with = SteamDateSerializer::class) val date: Instant? = null
)