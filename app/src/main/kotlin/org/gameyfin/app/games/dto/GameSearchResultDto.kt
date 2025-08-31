package org.gameyfin.app.games.dto

import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
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
    val originalIds: Map<String, ExternalProviderIdDto>
)

class UrlWithSourceDto(
    val url: String,
    val pluginId: String
)