package org.gameyfin.app.requests.dto

import org.gameyfin.pluginapi.gamemetadata.Platform
import java.time.Instant


class GameRequestCreationDto(
    val title: String,
    val platform: Platform,
    val release: Instant?
)