package org.gameyfin.app.collections.entities

import io.mockk.mockk
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
