package org.gameyfin.app.media

import jakarta.persistence.*
import org.springframework.content.commons.annotations.ContentId
import org.springframework.content.commons.annotations.ContentLength
import org.springframework.content.commons.annotations.MimeType

@Entity
class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Lob
    val originalUrl: String? = null,

    val type: ImageType,

    @ContentId
    var contentId: String? = null,

    @ContentLength
    var contentLength: Long? = null,

    @MimeType
    var mimeType: String? = null,

    var blurhash: String? = null
)

enum class ImageType {
    COVER,
    HEADER,
    SCREENSHOT,
    AVATAR
}