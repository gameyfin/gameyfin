package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.controller.GameControllerV1
import org.gameyfin.app.api.v1.dto.GameMediaV1
import org.gameyfin.app.api.v1.dto.GameV1
import org.gameyfin.app.api.v1.dto.PageV1
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.games.GameService
import org.gameyfin.app.libraries.LibraryCoreService
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameControllerV1Test {
    private val access = allowingAccess()
    private val catalog = mockk<CatalogServiceV1>()
    private val games = mockk<GameService>(relaxed = true)
    private val libraries = mockk<LibraryCoreService>(relaxed = true)
    private val controller = GameControllerV1(access, catalog, games, libraries)

    private val game = testGame().publicGame()
    private val emptyGames = PageV1<GameV1>(emptyList(), 0, 50, 0)

    @Test
    fun `games are listed and fetched with games read`() {
        every { catalog.games(any(), any(), any(), any(), any(), any(), any()) } returns emptyGames
        every { catalog.game("10") } returns game

        assertEquals(emptyGames, controller.games(0, 50, "title,desc", "q", "1", "2", "PC", "igdb", "42"))
        assertEquals(game, controller.game("10"))

        verify {
            catalog.games(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "title").and(Sort.by("id"))),
                "q", "1", "2", "PC", "igdb", "42"
            )
        }
        verify(exactly = 2) { access.require(ApiScope.GAMES_READ) }
    }

    @Test
    fun `game edits delegate to the catalog`() {
        every { catalog.patchGame("10", any()) } returns game
        assertEquals(game, controller.patchGame("10", mapOf("title" to "New")))
        verify { access.require(ApiScope.GAMES_WRITE) }
    }

    @Test
    fun `media replacement forwards uploads except the metadata part`() {
        val metadata = GameMediaV1()
        val request = MockMultipartHttpServletRequest().apply {
            addFile(MockMultipartFile("metadata", "{}".toByteArray()))
            addFile(MockMultipartFile("cover", "cover.png", "image/png", byteArrayOf(1)))
        }
        val files = slot<Map<String, org.springframework.web.multipart.MultipartFile>>()
        every { catalog.replaceMedia("10", metadata, capture(files)) } returns game

        assertEquals(game, controller.media("10", metadata, request))
        assertEquals(setOf("cover"), files.captured.keys)

        request.addFile(MockMultipartFile("cover", "second.png", "image/png", byteArrayOf(2)))
        assertFailsWith<IllegalArgumentException> { controller.media("10", metadata, request) }
    }

    @Test
    fun `game deletion removes the catalog entry`() {
        every { catalog.game("10") } returns game

        assertEquals(204, controller.deleteGame("10").statusCode.value())

        verifyOrder {
            access.require(ApiScope.GAMES_DELETE)
            libraries.deleteGameFromLibrary(10)
            games.delete(10)
        }
    }
}
