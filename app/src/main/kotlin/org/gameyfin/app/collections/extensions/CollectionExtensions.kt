package org.gameyfin.app.collections.extensions

import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata

/**
 * @param gameIds IDs of the games in this collection. Passed explicitly so they can be fetched via a projection
 * instead of initializing the (lazy) games association.
 */
fun Collection.toDto(gameIds: List<Long>): CollectionDto = CollectionDto(
    id = id!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
    name = name,
    description = description,
    gameIds = gameIds,
    metadata = this.metadata.toDto()
)

/**
 * Builds a patch containing only the given fields.
 * @param gameIds IDs of the games in this collection, required if [fields] contains [CollectionField.GAME_IDS]
 */
fun Collection.toPatchDto(fields: Set<CollectionField>, gameIds: List<Long>?): CollectionPatchDto? {
    if (fields.isEmpty()) return null

    var values = CollectionPatchValuesDto()
    for (field in fields.sorted()) {
        values = when (field) {
            CollectionField.NAME -> values.copy(name = name)
            CollectionField.DESCRIPTION -> values.copy(description = description)
            CollectionField.GAME_IDS -> values.copy(gameIds = gameIds ?: games.mapNotNull { it.id })
            CollectionField.METADATA -> values.copy(metadata = metadata.toDto())
        }
    }

    return CollectionPatchDto(updatedAt = updatedAt!!, fields = fields.sorted(), values = values)
}

fun CollectionCreateDto.toEntity(): Collection = Collection(
    name = name,
    description = description
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