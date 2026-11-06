package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.controller.MetadataControllerV1
import org.gameyfin.app.api.v1.dto.ExternalIdV1
import org.gameyfin.app.api.v1.dto.MetadataUrlV1
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameSearchResultDto
import org.gameyfin.app.games.dto.UrlWithSourceDto
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import java.time.Instant
import java.time.LocalDate
import kotlin.test.*

class MetadataControllerV1Test {
    private val access = allowingAccess()
    private val games = mockk<GameService>()
    private val controller = MetadataControllerV1(access, games)

    @Test
    fun `search maps provider results to public request compatible fields`() {
        val platforms = Platform.entries.take(2).toSet()
        every { games.getPotentialMatches("Game", platforms) } returns listOf(
            GameSearchResultDto(
                title = "Game", platforms = platforms,
                coverUrls = listOf(UrlWithSourceDto("https://example.test/cover", "igdb")),
                headerUrls = listOf(UrlWithSourceDto("https://example.test/header", "steam")),
                screenshotUrls = listOf(UrlWithSourceDto("https://example.test/screenshot", "steam")),
                release = Instant.parse("2020-01-02T23:30:00Z"),
                publishers = listOf("Publisher"), developers = listOf("Developer"),
                originalIds = mapOf("internal.Provider" to ExternalProviderIdDto("igdb", "42"))
            )
        )
        val result = controller.search("Game", platforms.map { it.name }).single()
        assertEquals("Game", result.title)
        assertEquals(platforms.map { it.name }, result.platforms)
        assertEquals(LocalDate.of(2020, 1, 2), result.release)
        assertEquals(listOf("Publisher"), result.publishers)
        assertEquals(listOf("Developer"), result.developers)
        assertEquals(listOf(MetadataUrlV1("https://example.test/cover", "igdb")), result.coverUrls)
        assertEquals(listOf(MetadataUrlV1("https://example.test/header", "steam")), result.headerUrls)
        assertEquals(listOf(MetadataUrlV1("https://example.test/screenshot", "steam")), result.screenshotUrls)
        assertEquals(listOf(ExternalIdV1("igdb", "42")), result.externalIds)
        verify { access.require(ApiScope.GAMES_READ) }
    }

    @Test
    fun `omitted platforms searches all and empty provider results return an empty list`() {
        every { games.getPotentialMatches("Game", emptySet()) } returns emptyList()
        assertTrue(controller.search("Game", null).isEmpty())
    }

    @Test
    fun `missing provider metadata remains nullable`() {
        every { games.getPotentialMatches(any(), any()) } returns listOf(
            GameSearchResultDto(title = "Game", platforms = null, coverUrls = null, headerUrls = null,
                release = null, publishers = null, developers = null, originalIds = emptyMap())
        )
        val result = controller.search("Game", null)
        val json = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(result)
        assertFalse(json.contains("originalIds"))
        assertNull(result.single().release)
        assertNull(result.single().platforms)
        assertTrue(result.single().externalIds.isEmpty())
    }

    @Test
    fun `invalid searches never reach providers`() {
        listOf("", "  ", "a".repeat(256)).forEach {
            assertFailsWith<IllegalArgumentException> { controller.search(it, null) }
        }
        assertFailsWith<IllegalArgumentException> { controller.search("Game", listOf("invalid")) }
        verify { games wasNot Called }
    }

    @Test
    fun `unauthorized searches never reach providers`() {
        every { access.require(ApiScope.GAMES_READ) } throws AccessDeniedException("denied")
        assertFailsWith<AccessDeniedException> { controller.search("Game", null) }
        verify { games wasNot Called }
    }
}
