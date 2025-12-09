package org.gameyfin.app.collections.dto

import java.time.Instant

data class CollectionMetadataDto(
    val displayOnHomepage: Boolean,
    val displayOrder: Int,
    val gamesAddedAt: Map<Long, Instant>
)

data class CollectionMetadataUpdateDto(
    val displayOnHomepage: Boolean?,
    val displayOrder: Int?
)