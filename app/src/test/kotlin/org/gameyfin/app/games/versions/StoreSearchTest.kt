package org.gameyfin.app.games.versions

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreSearchTest {

    @Test
    fun `search service ignores short queries and delegates to the provider of the store`() {
        val steam = mockk<GameStoreProvider>()
        val gog = mockk<GameStoreProvider>()
        every { gog.search("Stardew") } returns listOf(GameStoreProduct("1", "Stardew", "2016", "https://example.test/a.jpg"))
        val service = GameStoreSearchService(registryOf(activeStore(STEAM_INFO, steam), activeStore(GOG_INFO, gog)), StoreThumbnailProxy())

        assertTrue(service.search("gog", " a ").isEmpty())
        assertEquals(
            listOf(StoreSearchResultDto("1", "Stardew", "2016", "https://example.test/a.jpg")),
            service.search("gog", "  Stardew ")
        )
        verify(exactly = 0) { steam.search(any()) }
    }

    @Test
    fun `search service limits the number of results`() {
        val provider = mockk<GameStoreProvider>()
        every { provider.search("Hades") } returns (1..30).map { GameStoreProduct("$it", "Hades $it") }
        val service = GameStoreSearchService(registryOf(activeStore(GOG_INFO, provider)), StoreThumbnailProxy())

        assertEquals(10, service.search("gog", "Hades").size)
    }

    @Test
    fun `search service fails when no active plugin provides the store`() {
        val service = GameStoreSearchService(registryOf(activeStore(GOG_INFO)), StoreThumbnailProxy())

        assertFailsWith<StoreUnavailableException> { service.search("epic", "Hades") }
    }

    @Test
    fun `search queries lose punctuation but keep apostrophes`() {
        assertEquals("The Witcher 3 Wild Hunt Remastered", normalizeSearchQuery("The Witcher 3: Wild Hunt — Remastered"))
        assertEquals("Assassin's Creed", normalizeSearchQuery("  Assassin's Creed! "))
    }
}
