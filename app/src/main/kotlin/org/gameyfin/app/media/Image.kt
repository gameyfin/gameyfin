package org.gameyfin.app.media

import jakarta.persistence.*
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@BatchSize(size = 100)
class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
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
