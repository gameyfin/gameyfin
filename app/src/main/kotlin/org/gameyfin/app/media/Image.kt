package org.gameyfin.app.media

import jakarta.persistence.*

@Entity
class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Lob
    val originalUrl: String? = null,

    val type: ImageType,

    var contentId: String? = null,

    var contentLength: Long? = null,

    var mimeType: String? = null,

    var blurhash: String? = null
)

enum class ImageType {
    COVER,
    HEADER,
    SCREENSHOT,
    AVATAR
}