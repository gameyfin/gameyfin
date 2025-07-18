package org.gameyfin.app.games.entities

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.springframework.content.commons.annotations.ContentId
import org.springframework.content.commons.annotations.ContentLength
import org.springframework.content.commons.annotations.MimeType
import java.net.URL

@Entity
class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    val originalUrl: URL? = null,

    val type: ImageType,

    @ContentId
    var contentId: String? = null,

    @ContentLength
    var contentLength: Long? = null,

    @MimeType
    var mimeType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Image) return false
        return originalUrl.toString() == other.originalUrl.toString()
    }
}

enum class ImageType {
    COVER,
    HEADER,
    SCREENSHOT,
    AVATAR
}