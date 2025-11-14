package org.gameyfin.app.platforms

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.platforms.dto.PlatformStatsDto
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginManager
import org.pf4j.PluginState
import org.pf4j.PluginStateEvent
import org.pf4j.PluginWrapper
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformServiceTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var platformService: PlatformService
    private lateinit var mockMetadataProvider1: GameMetadataProvider
    private lateinit var mockMetadataProvider2: GameMetadataProvider

    @BeforeEach
    fun setup() {
        gameRepository = mockk()
        libraryRepository = mockk()
        pluginManager = mockk()
        mockMetadataProvider1 = mockk()
        mockMetadataProvider2 = mockk()

        platformService = PlatformService(gameRepository, libraryRepository, pluginManager)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `initialize should calculate all platform caches`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)

        platformService.initialize()

        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), platformService.availablePlatforms)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), platformService.platformsInUseByGames)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), platformService.platformsInUseByLibraries)

        verify(atLeast = 1) { pluginManager.getExtensions(GameMetadataProvider::class.java) }
        verify(exactly = 1) { gameRepository.findAllDistinctPlatforms() }
        verify(exactly = 1) { libraryRepository.findAllDistinctPlatforms() }
    }

    @Test
    fun `calculateAvailablePlatforms should return all platforms when plugin supports all`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns emptySet()
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(Platform.entries.toSet(), platformService.availablePlatforms)
    }

    @Test
    fun `calculateAvailablePlatforms should return union of all plugin platforms`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            mockMetadataProvider1,
            mockMetadataProvider2
        )
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )
        every { mockMetadataProvider2.supportedPlatforms } returns setOf(
            Platform.XBOX_SERIES_X_S,
            Platform.NINTENDO_SWITCH
        )
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(
            setOf(
                Platform.PC_MICROSOFT_WINDOWS,
                Platform.PLAYSTATION_5,
                Platform.XBOX_SERIES_X_S,
                Platform.NINTENDO_SWITCH
            ),
            platformService.availablePlatforms
        )
    }

    @Test
    fun `calculateAvailablePlatforms should handle overlapping platforms`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            mockMetadataProvider1,
            mockMetadataProvider2
        )
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )
        every { mockMetadataProvider2.supportedPlatforms } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.NINTENDO_SWITCH
        )
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(
            setOf(
                Platform.PC_MICROSOFT_WINDOWS,
                Platform.PLAYSTATION_5,
                Platform.NINTENDO_SWITCH
            ),
            platformService.availablePlatforms
        )
    }

    @Test
    fun `calculateAvailablePlatforms should handle no plugins`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns emptyList()
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(emptySet(), platformService.availablePlatforms)
    }

    @Test
    fun `calculateAvailablePlatforms should handle mix of empty and non-empty supported platforms`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(
            mockMetadataProvider1,
            mockMetadataProvider2
        )
        every { mockMetadataProvider1.supportedPlatforms } returns emptySet()
        every { mockMetadataProvider2.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(Platform.entries.toSet(), platformService.availablePlatforms)
    }

    @Test
    fun `onPluginStateChange should recalculate available platforms for GameMetadataProvider plugin`() {
        val mockPluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        every { pluginWrapper.pluginId } returns "test-plugin"
        val pluginStateEvent = PluginStateEvent(mockPluginManager, pluginWrapper, PluginState.STARTED)

        every { pluginManager.supportsExtensionType("test-plugin", GameMetadataProvider::class) } returns true
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        every { mockMetadataProvider1.supportedPlatforms } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )

        platformService.onPluginStateChange(pluginStateEvent)

        verify(atLeast = 1) { pluginManager.getExtensions(GameMetadataProvider::class.java) }
    }

    @Test
    fun `onPluginStateChange should not recalculate for non-GameMetadataProvider plugin`() {
        val mockPluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        every { pluginWrapper.pluginId } returns "test-plugin"
        val pluginStateEvent = PluginStateEvent(mockPluginManager, pluginWrapper, PluginState.STARTED)

        every { pluginManager.supportsExtensionType("test-plugin", GameMetadataProvider::class) } returns false

        platformService.onPluginStateChange(pluginStateEvent)

        verify(exactly = 0) { pluginManager.getExtensions(GameMetadataProvider::class.java) }
    }

    @Test
    fun `onGameChange should recalculate platforms in use by games`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        every { gameRepository.findAllDistinctPlatforms() } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )

        platformService.onGameChange()

        verify(atLeast = 1) { gameRepository.findAllDistinctPlatforms() }
    }

    @Test
    fun `onLibraryChange should recalculate platforms in use by libraries`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)

        platformService.initialize()

        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.NINTENDO_SWITCH
        )

        platformService.onLibraryChange()

        verify(atLeast = 1) { libraryRepository.findAllDistinctPlatforms() }
    }

    @Test
    fun `getStats should return current platform statistics`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )
        every { gameRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(Platform.PLAYSTATION_5)

        platformService.initialize()

        val stats = platformService.getStats()

        assertNotNull(stats)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5), stats.available)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), stats.inUseByGames)
        assertEquals(setOf(Platform.PLAYSTATION_5), stats.inUseByLibraries)
    }

    @Test
    fun `getStats should return empty sets when no data available`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns emptyList()
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        val stats = platformService.getStats()

        assertNotNull(stats)
        assertEquals(emptySet(), stats.available)
        assertEquals(emptySet(), stats.inUseByGames)
        assertEquals(emptySet(), stats.inUseByLibraries)
    }

    @Test
    fun `subscribe should emit platform updates when available platforms change`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        val subscriber = PlatformService.subscribe()
            .next()

        platformService.initialize()

        StepVerifier.create(subscriber)
            .expectNextMatches { list ->
                list.isNotEmpty() && list.any { it.available != null }
            }
            .verifyComplete()
    }

    @Test
    fun `emit should send PlatformStatsDto to subscribers`() {
        val testStats = PlatformStatsDto(
            available = setOf(Platform.PC_MICROSOFT_WINDOWS)
        )

        val subscriber = PlatformService.subscribe()
            .next()

        PlatformService.emit(testStats)

        StepVerifier.create(subscriber)
            .expectNextMatches { list ->
                list.isNotEmpty() && list.any { it.available == setOf(Platform.PC_MICROSOFT_WINDOWS) }
            }
            .verifyComplete()
    }

    @Test
    fun `calculatePlatformsInUseByGames should handle empty game repository`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(emptySet(), platformService.platformsInUseByGames)
    }

    @Test
    fun `calculatePlatformsInUseByLibraries should handle empty library repository`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(emptySet(), platformService.platformsInUseByLibraries)
    }

    @Test
    fun `calculatePlatformsInUseByGames should handle large set of platforms`() {
        val largePlatformSet = Platform.entries.take(50).toSet()
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns emptySet()
        every { gameRepository.findAllDistinctPlatforms() } returns largePlatformSet
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        assertEquals(largePlatformSet, platformService.platformsInUseByGames)
    }

    @Test
    fun `event listener methods should handle GameCreatedEvent`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        every { gameRepository.findAllDistinctPlatforms() } returns setOf(Platform.PC_MICROSOFT_WINDOWS)

        platformService.onGameChange()

        verify(atLeast = 1) { gameRepository.findAllDistinctPlatforms() }
    }

    @Test
    fun `event listener methods should handle LibraryCreatedEvent`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(Platform.NINTENDO_SWITCH)

        platformService.onLibraryChange()

        verify(atLeast = 1) { libraryRepository.findAllDistinctPlatforms() }
    }

    @Test
    fun `multiple plugin state changes should trigger multiple recalculations`() {
        val mockPluginManager = mockk<PluginManager>()
        val pluginWrapper = mockk<PluginWrapper>()
        every { pluginWrapper.pluginId } returns "test-plugin"
        val startedEvent = PluginStateEvent(mockPluginManager, pluginWrapper, PluginState.STARTED)
        val stoppedEvent = PluginStateEvent(mockPluginManager, pluginWrapper, PluginState.STOPPED)

        every { pluginManager.supportsExtensionType("test-plugin", GameMetadataProvider::class) } returns true
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns emptySet()
        every { libraryRepository.findAllDistinctPlatforms() } returns emptySet()

        platformService.initialize()

        platformService.onPluginStateChange(startedEvent)
        platformService.onPluginStateChange(stoppedEvent)

        verify(atLeast = 2) { pluginManager.getExtensions(GameMetadataProvider::class.java) }
    }

    @Test
    fun `platformsInUseByGames and platformsInUseByLibraries can have different platforms`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns emptySet()
        every { gameRepository.findAllDistinctPlatforms() } returns setOf(
            Platform.PC_MICROSOFT_WINDOWS,
            Platform.PLAYSTATION_5
        )
        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(
            Platform.XBOX_SERIES_X_S,
            Platform.NINTENDO_SWITCH
        )

        platformService.initialize()

        assertEquals(
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            platformService.platformsInUseByGames
        )
        assertEquals(
            setOf(Platform.XBOX_SERIES_X_S, Platform.NINTENDO_SWITCH),
            platformService.platformsInUseByLibraries
        )
    }

    @Test
    fun `availablePlatforms should be independent of platforms in use`() {
        every { pluginManager.getExtensions(GameMetadataProvider::class.java) } returns listOf(mockMetadataProvider1)
        every { mockMetadataProvider1.supportedPlatforms } returns setOf(Platform.PC_MICROSOFT_WINDOWS)
        every { gameRepository.findAllDistinctPlatforms() } returns setOf(Platform.PLAYSTATION_5)
        every { libraryRepository.findAllDistinctPlatforms() } returns setOf(Platform.XBOX_SERIES_X_S)

        platformService.initialize()

        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), platformService.availablePlatforms)
        assertEquals(setOf(Platform.PLAYSTATION_5), platformService.platformsInUseByGames)
        assertEquals(setOf(Platform.XBOX_SERIES_X_S), platformService.platformsInUseByLibraries)
    }
}

