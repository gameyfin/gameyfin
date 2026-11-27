package org.gameyfin.app.collections.extensions

import org.gameyfin.app.collections.dto.CollectionMetadataDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionExtensionsTest {

    @Test
    fun toDtoUsesProvidedGameIds() {
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        val c = Collection(name = "Favorites", description = "Desc").apply {
            id = 1L; this.createdAt = createdAt; updatedAt = createdAt
            metadata = CollectionMetadata(displayOnHomepage = false, displayOrder = 2)
        }

        val dto = c.toDto(listOf(11L, 12L))

        assertEquals(1L, dto.id)
        assertEquals("Favorites", dto.name)
        assertEquals("Desc", dto.description)
        assertEquals(createdAt, dto.createdAt)
        assertEquals(createdAt, dto.updatedAt)
        assertEquals(listOf(11L, 12L), dto.gameIds)
        assertEquals(CollectionMetadataDto(false, 2, emptyMap()), dto.metadata)
    }
}
