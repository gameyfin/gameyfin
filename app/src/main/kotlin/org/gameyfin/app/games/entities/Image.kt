package org.gameyfin.app.games.entities

import jakarta.persistence.*
import org.springframework.content.commons.annotations.ContentId
import org.springframework.content.commons.annotations.ContentLength
import org.springframework.content.commons.annotations.MimeType
import java.net.URL

@Entity
@EntityListeners(ImageEntityListener::class)
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

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + originalUrl?.toString().hashCode()
        return result
    }
}

enum class ImageType {
    COVER,
    HEADER,
    SCREENSHOT,
    AVATAR
}