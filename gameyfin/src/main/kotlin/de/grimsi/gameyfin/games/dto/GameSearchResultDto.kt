package de.grimsi.gameyfin.games.dto

import java.time.Instant
import java.util.*

class GameSearchResultDto(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val coverUrl: String?,
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