package org.gameyfin.app.collections.extensions

import org.gameyfin.app.collections.dto.CollectionCreateDto
import org.gameyfin.app.collections.dto.CollectionDto
import org.gameyfin.app.collections.dto.CollectionMetadataDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata
import org.gameyfin.app.collections.entities.CollectionVisibility

/**
 * @param gameIds IDs of the games in this collection. Passed explicitly so they can be fetched via a projection
 * instead of initializing the (lazy) games association.
 */
fun Collection.toDto(gameIds: List<Long>): CollectionDto = CollectionDto(
    id = id!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
    name = displayName,
    description = description,
    gameIds = gameIds,
    metadata = this.metadata.toDto(),
    kind = kind,
    visibility = visibility,
    ownerId = owner?.id,
    ownerName = owner?.username,
    smartEnabled = smartEnabled
)

fun CollectionCreateDto.toEntity(): Collection = Collection(
    name = name,
    description = description,
    visibility = visibility ?: CollectionVisibility.PUBLIC
)

fun CollectionMetadata.toDto(): CollectionMetadataDto {
    return CollectionMetadataDto(
        displayOnHomepage = this.displayOnHomepage,
        displayOrder = this.displayOrder,
        gamesAddedAt = this.gamesAddedAt.toMap()
    )
}

fun CollectionMetadataDto.toEntity(): CollectionMetadata {
    return CollectionMetadata(
        displayOnHomepage = this.displayOnHomepage,
        displayOrder = this.displayOrder,
        gamesAddedAt = this.gamesAddedAt.toMutableMap()
    )
}
