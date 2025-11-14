package org.gameyfin.app.platforms

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.app.platforms.dto.PlatformStatsDto
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlatformEndpointTest {

    private lateinit var platformService: PlatformService
    private lateinit var platformEndpoint: PlatformEndpoint

    @BeforeEach
    fun setup() {
        platformService = mockk()
        platformEndpoint = PlatformEndpoint(platformService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `subscribe should return non-null Flux from PlatformService companion object`() {
        val result = platformEndpoint.subscribe()
        assertNotNull(result)
    }

    @Test
    fun `getStats should return stats from platformService`() {
        val expectedStats = PlatformStatsDto(
            available = setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            inUseByGames = setOf(Platform.PC_MICROSOFT_WINDOWS),
            inUseByLibraries = setOf(Platform.PLAYSTATION_5)
        )

        every { platformService.getStats() } returns expectedStats

        val result = platformEndpoint.getStats()

        assertEquals(expectedStats, result)
        verify(exactly = 1) { platformService.getStats() }
    }

    @Test
    fun `getStats should return empty sets when no platforms available`() {
        val expectedStats = PlatformStatsDto(
            available = emptySet(),
            inUseByGames = emptySet(),
            inUseByLibraries = emptySet()
        )

        every { platformService.getStats() } returns expectedStats

        val result = platformEndpoint.getStats()

        assertEquals(expectedStats, result)
        verify(exactly = 1) { platformService.getStats() }
    }

    @Test
    fun `getStats should handle null values in PlatformStatsDto`() {
        val expectedStats = PlatformStatsDto(
            available = null,
            inUseByGames = null,
            inUseByLibraries = null
        )

        every { platformService.getStats() } returns expectedStats

        val result = platformEndpoint.getStats()

        assertEquals(expectedStats, result)
        verify(exactly = 1) { platformService.getStats() }
    }

    @Test
    fun `getStats should handle partial null values in PlatformStatsDto`() {
        val expectedStats = PlatformStatsDto(
            available = setOf(Platform.NINTENDO_SWITCH),
            inUseByGames = null,
            inUseByLibraries = null
        )

        every { platformService.getStats() } returns expectedStats

        val result = platformEndpoint.getStats()

        assertEquals(expectedStats, result)
        verify(exactly = 1) { platformService.getStats() }
    }

    @Test
    fun `getStats should handle large set of platforms`() {
        val allPlatforms = Platform.entries.toSet()
        val expectedStats = PlatformStatsDto(
            available = allPlatforms,
            inUseByGames = allPlatforms,
            inUseByLibraries = allPlatforms
        )

        every { platformService.getStats() } returns expectedStats

        val result = platformEndpoint.getStats()

        assertEquals(expectedStats, result)
        verify(exactly = 1) { platformService.getStats() }
    }
}

