package org.gameyfin.app.collections.entities

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import kotlin.test.*

class CollectionEntityTest {
    @Test
    fun addAndRemoveGameMaintainBidirectionalAssociation() {
        val collection = Collection(name = "Test")
        val game = Game(
            library = mockk<Library>(relaxed = true),
            metadata = GameMetadata(path = "p")
        )
        // ensure id not required for association behavior
        assertEquals(0, collection.games.size)
        assertFalse(game.collections.contains(collection))

        collection.addGame(game)
        assertEquals(1, collection.games.size)
        assertTrue(game.collections.contains(collection))

        collection.removeGame(game)
        assertEquals(0, collection.games.size)
        assertFalse(game.collections.contains(collection))
    }

    @Test
    fun addGameTracksTimestamp() {
        val collection = Collection(name = "Test")
        val game = mockk<Game>(relaxed = true)
        val gameId = 123L
        every { game.id } returns gameId
        every { game.collections } returns mutableListOf()

        // Before adding, no timestamp should exist
        assertNull(collection.metadata.gamesAddedAt[gameId])

        collection.addGame(game)

        // After adding, timestamp should be recorded
        assertNotNull(collection.metadata.gamesAddedAt[gameId])
        assertTrue(collection.metadata.gamesAddedAt.containsKey(gameId))
    }

    @Test
    fun removeGameRemovesTimestamp() {
        val collection = Collection(name = "Test")
        val game = mockk<Game>(relaxed = true)
        val gameId = 456L
        every { game.id } returns gameId
        every { game.collections } returns mutableListOf()

        collection.addGame(game)
        assertNotNull(collection.metadata.gamesAddedAt[gameId])

        collection.removeGame(game)

        // After removing, timestamp should be removed
        assertNull(collection.metadata.gamesAddedAt[gameId])
        assertFalse(collection.metadata.gamesAddedAt.containsKey(gameId))
    }
}
