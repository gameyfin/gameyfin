package org.gameyfin.app.media

data class ImageDto(
    val id: String,
    val type: ImageType,
    val blurhash: String?
)