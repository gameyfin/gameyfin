package org.gameyfin.app.games.dto

import java.time.Instant
import java.util.*

class GameSearchResultDto(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val coverUrls: List<UrlWithSourceDto>?,
    val headerUrls: List<UrlWithSourceDto>?,
    val release: Instant?,
    val publishers: Collection<String>?,
    val developers: Collection<String>?,
    val originalIds: Map<String, OriginalIdDto>
)

class OriginalIdDto(
    val pluginId: String,
    val originalId: String,
) {
    override fun toString(): String {
        return "$pluginId:$originalId"
    }
}

class UrlWithSourceDto(
    val url: String,
    val pluginId: String
)