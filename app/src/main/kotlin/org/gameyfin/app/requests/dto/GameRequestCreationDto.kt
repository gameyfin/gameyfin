package org.gameyfin.app.requests.dto

import java.time.Instant


class GameRequestCreationDto(
    val title: String,
    val release: Instant,
    val externalProviderIds: Map<String, String>
)