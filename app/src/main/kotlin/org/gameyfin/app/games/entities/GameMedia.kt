package org.gameyfin.app.games.entities

import jakarta.persistence.*
import org.gameyfin.app.media.Image
import java.net.URI

/** One screenshot or video in a game's ordered gallery. */
@Embeddable
class GameMedia(
    @ManyToOne(cascade = [CascadeType.MERGE, CascadeType.REFRESH], fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    var image: Image? = null,

    @Column(name = "video_url", length = 8192)
    var videoUrl: URI? = null
)
