package de.grimsi.gameyfin.users.entities

import jakarta.annotation.Nullable
import jakarta.persistence.Embeddable
import org.springframework.content.commons.annotations.ContentId
import org.springframework.content.commons.annotations.ContentLength
import org.springframework.content.commons.annotations.MimeType


@Embeddable
class Avatar(
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