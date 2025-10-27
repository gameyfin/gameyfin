package org.gameyfin.plugins.metadata.igdb.mapper

import com.api.igdb.utils.ImageSize
import com.api.igdb.utils.ImageType
import com.api.igdb.utils.imageBuilder
import proto.Artwork
import proto.Cover
import proto.GameVideo
import proto.Screenshot
import java.net.URI

class MediaMapper {
    companion object {
        fun cover(cover: Cover): URI? {
            if (cover.imageId.isEmpty()) return null
            return URI(imageBuilder(cover.imageId, ImageSize.COVER_BIG, ImageType.PNG))
        }

        fun header(header: Artwork): URI {
            return URI(imageBuilder(header.imageId, ImageSize.FHD, ImageType.PNG))
        }

        fun screenshot(screenshot: Screenshot): URI {
            return URI(imageBuilder(screenshot.imageId, ImageSize.FHD, ImageType.PNG))
        }

        fun video(video: GameVideo): URI {
            return URI("https://www.youtube.com/watch?v=${video.videoId}")
        }
    }
}