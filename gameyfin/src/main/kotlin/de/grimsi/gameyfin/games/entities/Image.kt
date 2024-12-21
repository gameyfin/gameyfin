package de.grimsi.gameyfin.games.entities

import jakarta.annotation.Nullable
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

    val originalUrl: URL,

    val type: ImageType,

    @ContentId
    @Nullable
    var contentId: String? = null,

    @ContentLength
    @Nullable
    var contentLength: Long? = null,

    @MimeType
    @Nullable
    var mimeType: String? = null
)

enum class ImageType {
    COVER,
    SCREENSHOT
}