package org.gameyfin.app.games

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class GameMediaSearchTest {
    @Test
    fun `search merges screenshots with plugin attribution and deduplicates URLs`() {
        val manager = mockk<GameyfinPluginManager>()
        val plugins = mockk<PluginService>()
        val first = object : GameMetadataProvider by TestProvider(GameMetadata(
            originalId = "first", title = "Game",
            screenshotUrls = linkedSetOf(URI("https://example.com/shared.png"), URI("https://example.com/first.png"))
        )) {}
        val second = object : GameMetadataProvider by TestProvider(GameMetadata(
            originalId = "second", title = "Game",
            screenshotUrls = linkedSetOf(URI("https://example.com/shared.png"), URI("https://example.com/second.png"))
        )) {}
        every { manager.getExtensions(GameMetadataProvider::class.java) } returns listOf(first, second)
        every { plugins.getPluginManagementEntry(first.javaClass) } returns mockk<PluginManagementEntry> {
            every { pluginId } returns "first-plugin"
            every { priority } returns 10
        }
        every { plugins.getPluginManagementEntry(second.javaClass) } returns mockk<PluginManagementEntry> {
            every { pluginId } returns "second-plugin"
            every { priority } returns 5
        }
        val service = GameService(mockk(), manager, plugins, mockk(), mockk(), mockk(), mockk(), mockk())
        val results = service.getPotentialMatches("Game", emptySet())
        assertEquals(1, results.size)
        assertEquals(mapOf(
            "https://example.com/shared.png" to "first-plugin",
            "https://example.com/first.png" to "first-plugin",
            "https://example.com/second.png" to "second-plugin"
        ), results.single().screenshotUrls!!.associate { it.url to it.pluginId })
    }
}
