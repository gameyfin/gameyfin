package org.gameyfin.app.core.sync

import org.gameyfin.app.collections.dto.CollectionField
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.dto.LibraryField
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityChangeSetTest {

    @Test
    fun `addAssociationFields should require timestamp bump`() {
        val change = EntityChange<GameField>()

        change.addAssociationFields(listOf(GameField.IMAGES))

        assertTrue(change.requiresTimestampBump)
        assertEquals(setOf(GameField.IMAGES), change.fields)
    }

    @Test
    fun `addAssociationFields should ignore empty field lists`() {
        val change = EntityChange<GameField>()

        change.addAssociationFields(emptyList())

        assertFalse(change.requiresTimestampBump)
        assertTrue(change.fields.isEmpty())
    }

    @Test
    fun `finalizeTransaction should skip bump if row has been updated`() {
        val change = EntityChange<GameField>().apply {
            rowUpdated = true
            addAssociationFields(listOf(GameField.IMAGES))
        }

        change.finalizeTransaction()

        assertFalse(change.requiresTimestampBump)
    }

    @Test
    fun `finalizeTransaction should keep bump if only associations changed`() {
        val change = EntityChange<GameField>().apply { addAssociationFields(listOf(GameField.IMAGES)) }

        change.finalizeTransaction()

        assertTrue(change.requiresTimestampBump)
    }

    @Test
    fun `isTransient should be true for entities created and deleted`() {
        val change = EntityChange<GameField>().apply { created = true; deleted = true }
        assertTrue(change.isTransient)
        assertFalse(EntityChange<GameField>().apply { created = true }.isTransient)
    }

    @Test
    fun `merge should combine changes of multiple transactions`() {
        val first = EntityChangeSet().apply {
            game(1L).apply { rowUpdated = true; fields += GameField.TITLE }
            library(2L).created = true
            imageIds += 10L
        }.finalizeTransaction()
        val second = EntityChangeSet().apply {
            game(1L).addAssociationFields(listOf(GameField.IMAGES))
            collection(3L).deleted = true
            gameFieldMetadataIds += 20L
            directoryMappingIds += 30L
        }.finalizeTransaction()

        val merged = EntityChangeSet.merge(listOf(first, second))

        val game = merged.games.getValue(1L)
        assertEquals(setOf(GameField.TITLE, GameField.IMAGES), game.fields)
        assertTrue(game.rowUpdated)
        // The bump has been decided per transaction, the second transaction did not update the row
        assertTrue(game.requiresTimestampBump)
        assertTrue(merged.libraries.getValue(2L).created)
        assertTrue(merged.collections.getValue(3L).deleted)
        assertEquals(setOf(10L), merged.imageIds)
        assertEquals(setOf(20L), merged.gameFieldMetadataIds)
        assertEquals(setOf(30L), merged.directoryMappingIds)
    }

    @Test
    fun `isEmpty should consider child entity IDs`() {
        assertTrue(EntityChangeSet().isEmpty())
        assertFalse(EntityChangeSet().apply { imageIds += 1L }.isEmpty())
        assertFalse(EntityChangeSet().apply { collection(1L) }.isEmpty())
    }

    @Test
    fun `changedGameMetadataFields should only return changed fields`() {
        val previous = GameMetadata(path = "/a", fileSize = 1L, downloadCount = 0)
        val current = GameMetadata(path = "/a", fileSize = 2L, downloadCount = 1)

        assertEquals(
            listOf(GameField.FILE_SIZE, GameField.DOWNLOAD_COUNT),
            EntityFieldMappings.changedGameMetadataFields(previous, current)
        )
    }

    @Test
    fun `changedGameMetadataFields should return all fields if previous state is unknown`() {
        assertEquals(
            EntityFieldMappings.GAME_METADATA_FIELDS,
            EntityFieldMappings.changedGameMetadataFields(null, GameMetadata(path = "/a"))
        )
    }

    @Test
    fun `changedGameFields should map dirty properties`() {
        val previous = GameMetadata(path = "/a", matchConfirmed = false)
        val current = GameMetadata(path = "/a", matchConfirmed = true)

        val result = EntityFieldMappings.changedGameFields(
            listOf("title", "metadata", "updatedAt", "coverImage"),
            { previous },
            { current }
        )

        assertEquals(setOf(GameField.TITLE, GameField.MATCH_CONFIRMED, GameField.COVER), result)
    }

    @Test
    fun `changedGameFields should return all fields if dirty properties are unknown`() {
        assertEquals(GameField.entries.toSet(), EntityFieldMappings.changedGameFields(null, { null }, { null }))
    }

    @Test
    fun `changedLibraryFields and changedCollectionFields should map dirty properties`() {
        assertEquals(
            setOf(LibraryField.NAME, LibraryField.PLATFORMS),
            EntityFieldMappings.changedLibraryFields(listOf("name", "platforms", "updatedAt"))
        )
        assertEquals(LibraryField.entries.toSet(), EntityFieldMappings.changedLibraryFields(null))
        assertEquals(
            setOf(CollectionField.METADATA, CollectionField.GAME_IDS),
            EntityFieldMappings.changedCollectionFields(listOf("metadata.gamesAddedAt", "games"))
        )
    }

    @Test
    fun `propertyPathFromRole should strip owner entity name`() {
        assertEquals(
            "metadata.fields",
            EntityFieldMappings.propertyPathFromRole(
                "org.gameyfin.app.games.entities.Game.metadata.fields",
                "org.gameyfin.app.games.entities.Game"
            )
        )
        assertEquals("images", EntityFieldMappings.propertyPathFromRole("some.Entity.images", null))
    }
}
