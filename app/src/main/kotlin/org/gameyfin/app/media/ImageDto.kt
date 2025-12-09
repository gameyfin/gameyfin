package org.gameyfin.app.media

data class ImageDto(
    val id: Long,
    val type: ImageType,
    val blurhash: String?
)