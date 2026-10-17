package org.gameyfin.app.games

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.entities.GameFieldUserSource
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.UserService
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.gameyfin.pluginapi.gamemetadata.GameMetadata as PluginMetadata

class GameMetadataRefreshTest {
    private val repository = mockk<GameRepository>()
    private val pluginManager = mockk<GameyfinPluginManager>()
    private val pluginService = mockk<PluginService>()
    private val images = mockk<ImageService>()
    private val service = GameService(
        repository, pluginManager, pluginService, mockk<ConfigService>(), mockk<CompanyService>(),
        mockk<UserService>(), images, mockk<FilesystemService>()
    )
    private val entry = PluginManagementEntry("gog", enabled = true, priority = 1)
    private val originalId = "51142058780705891"
    private val library = Library(id = 1, name = "PC", platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS))
    private val game = Game(
        id = 6, library = library, title = "Example Game", summary = "Existing description",
        platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS),
        metadata = GameMetadata(
            path = "/games/Example Game", originalIds = mapOf(entry to originalId),
            downloadCount = 17, matchConfirmed = true
        )
    )

    @BeforeEach
    fun setup() {
        every { repository.findByIdOrNull(6) } returns game
    }

    @AfterEach
    fun cleanup() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `rescans recover the saved ID after cache eviction and provider restart`() {
        var provider = RefreshProvider(emptyList())
        repeat(3) { scan ->
            if (scan == 2) provider = RefreshProvider(emptyList())
            provider.cache.clear()
            provider.searchResults = listOf(metadata(description = "Scan $scan"))
            install(provider)

            val result = service.updateMetadata(game)

            assertSame(game, result)
            assertEquals("Scan $scan", game.summary)
            assertEquals(mapOf(entry to originalId), game.metadata.originalIds)
            assertEquals(6L, game.id)
            assertEquals("/games/Example Game", game.metadata.path)
            assertEquals(17, game.metadata.downloadCount)
        }
        assertEquals(listOf("Example Game"), provider.titleSearches)
    }

    @Test
    fun `title recovery chooses the saved ID among similarly named editions`() {
        val provider = RefreshProvider(listOf(
            metadata(id = "different-edition", description = "Wrong edition"),
            metadata(description = "Correct edition")
        ))
        install(provider)

        assertNotNull(service.updateMetadata(game))

        assertEquals("Correct edition", game.summary)
        assertEquals(mapOf(entry to originalId), game.metadata.originalIds)
    }

    @Test
    fun `title recovery never replaces a saved match with another external ID`() {
        val provider = RefreshProvider(listOf(metadata(id = "different-edition")))
        install(provider)

        assertNull(service.updateMetadata(game))

        assertEquals(listOf("Example Game"), provider.titleSearches)
        assertEquals("Existing description", game.summary)
        assertEquals(mapOf(entry to originalId), game.metadata.originalIds)
        verify(exactly = 0) { images.createOrGet(any()) }
    }

    @Test
    fun `successful ID lookup does not perform a title search`() {
        val provider = RefreshProvider(emptyList()).apply { cache[originalId] = metadata() }
        install(provider)

        assertNotNull(service.updateMetadata(game))

        assertEquals(emptyList(), provider.titleSearches)
        assertEquals("Updated description", game.summary)
    }

    @Test
    fun `failed recovery leaves existing metadata and identifiers intact`() {
        val provider = RefreshProvider(emptyList()).apply { failSearch = true }
        install(provider)
        every { pluginManager.getPluginForExtension(provider::class) } returns null

        assertNull(service.updateMetadata(game))

        assertEquals(listOf("Example Game"), provider.titleSearches)
        assertEquals("Existing description", game.summary)
        assertEquals(mapOf(entry to originalId), game.metadata.originalIds)
    }

    @Test
    fun `an unavailable provider does not prevent another saved provider refreshing`() {
        val missing = PluginManagementEntry("uninstalled", enabled = true)
        game.metadata.originalIds = linkedMapOf(missing to "missing-id", entry to originalId)
        val provider = RefreshProvider(emptyList()).apply { cache[originalId] = metadata() }
        install(provider)
        every { pluginManager.getExtensions("uninstalled") } returns emptyList<Any>()

        assertNotNull(service.updateMetadata(game))

        assertEquals("Updated description", game.summary)
        assertEquals(mapOf(missing to "missing-id", entry to originalId), game.metadata.originalIds)
    }

    @Test
    fun `recovery preserves user supplied fields`() {
        val userSource = GameFieldMetadata(source = GameFieldUserSource(user = mockk()))
        game.summary = "My notes"
        game.metadata.fields["summary"] = userSource
        val provider = RefreshProvider(listOf(metadata().copy(userRating = 90)))
        install(provider)

        assertNotNull(service.updateMetadata(game))

        assertEquals("My notes", game.summary)
        assertSame(userSource, game.metadata.fields["summary"])
        assertEquals(90, game.userRating)
    }

    @Test
    fun `manual matching does not search by title when a selected ID is unavailable`() {
        val provider = RefreshProvider(listOf(metadata()))
        install(provider)

        val result = service.matchManually(
            mapOf(provider.javaClass.name to ExternalProviderIdDto(entry.pluginId, originalId)),
            Path.of(game.metadata.path), library, persist = false
        )

        assertNull(result)
        assertEquals(emptyList(), provider.titleSearches)
    }

    private fun install(provider: RefreshProvider) {
        every { pluginManager.getExtensions(entry.pluginId) } returns listOf(provider)
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(provider)
        every { pluginService.getPluginManagementEntry(provider.javaClass) } returns entry
    }

    private fun metadata(id: String = originalId, description: String = "Updated description") = PluginMetadata(
        originalId = id, title = "Example Game", description = description,
        platforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
    )

    // Models a provider that can search uncached entries but cannot retrieve their IDs after restart.
    private class RefreshProvider(var searchResults: List<PluginMetadata>) : GameMetadataProvider {
        val cache = mutableMapOf<String, PluginMetadata>()
        val titleSearches = mutableListOf<String>()
        var failSearch = false
        override val supportedPlatforms = setOf(Platform.PC_MICROSOFT_WINDOWS)
        override fun fetchById(id: String): PluginMetadata? = cache[id]
        override fun fetchByTitle(gameTitle: String, platformFilter: Set<Platform>, maxResults: Int): List<PluginMetadata> {
            titleSearches.add(gameTitle)
            if (failSearch) throw IllegalStateException("Provider unavailable")
            return searchResults.take(maxResults).also { matches ->
                matches.forEach { cache[it.originalId] = it }
            }
        }
    }
}
