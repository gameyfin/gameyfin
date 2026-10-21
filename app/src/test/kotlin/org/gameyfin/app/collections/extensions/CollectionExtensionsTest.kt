package org.gameyfin.app.collections.extensions

import io.mockk.mockk
import org.gameyfin.app.collections.dto.CollectionField
import org.gameyfin.app.collections.dto.CollectionMetadataDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import java.time.Instant
import kotlin.test.*

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

    @Test
    fun toPatchDtoContainsOnlyRequestedFields() {
        val c = Collection(name = "Renamed", description = "Desc").apply {
            id = 1L; createdAt = Instant.now(); updatedAt = createdAt
        }

        val patch = c.toPatchDto(setOf(CollectionField.NAME), gameIds = null)

        assertNotNull(patch)
        assertEquals(listOf(CollectionField.NAME), patch.fields)
        assertEquals("Renamed", patch.values.name)
        assertNull(patch.values.description)
        assertNull(patch.values.gameIds)
        assertEquals(c.updatedAt, patch.updatedAt)
    }

    @Test
    fun toPatchDtoUsesProvidedGameIds() {
        val c = Collection(name = "C").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }

        val patch = c.toPatchDto(setOf(CollectionField.GAME_IDS, CollectionField.METADATA), gameIds = listOf(5L))

        assertNotNull(patch)
        assertEquals(listOf(CollectionField.GAME_IDS, CollectionField.METADATA), patch.fields)
        assertEquals(listOf(5L), patch.values.gameIds)
        assertNotNull(patch.values.metadata)
    }

    @Test
    fun toPatchDtoFallsBackToAssociationForGameIds() {
        val c = Collection(name = "C").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        val g = Game(library = mockk<Library>(relaxed = true), metadata = GameMetadata(path = "p")).apply { id = 21L }
        c.addGame(g)

        val patch = c.toPatchDto(setOf(CollectionField.GAME_IDS), gameIds = null)

        assertEquals(listOf(21L), patch?.values?.gameIds)
    }

    @Test
    fun toPatchDtoReturnsNullWithoutFields() {
        val c = Collection(name = "C").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }

        assertNull(c.toPatchDto(emptySet(), gameIds = null))
    }
}
