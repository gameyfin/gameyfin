package org.gameyfin.app.games.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.content.commons.annotations.ContentId
import org.springframework.content.commons.annotations.ContentLength
import org.springframework.content.commons.annotations.MimeType

@Entity
class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    val originalUrl: String? = null,

    val type: ImageType,

    @ContentId
    var contentId: String? = null,

    @ContentLength
    var contentLength: Long? = null,

    @MimeType
    var mimeType: String? = null
)

enum class ImageType {
    COVER,
    HEADER,
    SCREENSHOT,
    AVATAR
}