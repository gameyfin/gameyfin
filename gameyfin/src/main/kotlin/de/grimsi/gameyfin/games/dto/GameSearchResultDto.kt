package de.grimsi.gameyfin.games.dto

import java.time.Instant

class GameSearchResultDto(
    val title: String,
    val coverUrl: String,
    val release: Instant,
    val publishers: Collection<String>?,
    val developers: Collection<String>?,
    val originalIds: Map<String, String>
)