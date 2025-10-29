package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.slf4j.LoggerFactory

class PlayerPerspectiveMapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun playerPerspective(perspective: proto.PlayerPerspective): PlayerPerspective {
            return when (perspective.slug) {
                "first-person" -> PlayerPerspective.FIRST_PERSON
                "third-person" -> PlayerPerspective.THIRD_PERSON
                "bird-view-isometric" -> PlayerPerspective.BIRD_VIEW_ISOMETRIC
                "bird-view-slash-isometric" -> PlayerPerspective.BIRD_VIEW_ISOMETRIC
                "side-view" -> PlayerPerspective.SIDE_VIEW
                "text" -> PlayerPerspective.TEXT
                "auditory" -> PlayerPerspective.AUDITORY
                "virtual-reality" -> PlayerPerspective.VIRTUAL_REALITY
                else -> {
                    log.warn("Unknown player perspective: {}", perspective.slug)
                    PlayerPerspective.UNKNOWN
                }
            }
        }
    }
}