package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameMediaControllerTest {
    private val service = mockk<GameService>()
    private val libraries = mockk<org.gameyfin.app.libraries.LibraryCoreService>()
    private val controller = GameMediaController(service, libraries)
    private val update = GameUpdateDto(
        id = 1, title = null, platforms = null, release = null, coverUrl = null, headerUrl = null,
        comment = null, summary = null, developers = null, publishers = null, genres = null, themes = null,
        features = null, perspectives = null, keywords = null, metadata = null,
        cover = GameImageSelectionDto(uploadKey = "cover")
    )

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `anonymous and regular users cannot save uploaded media`() {
        val request = MockMultipartHttpServletRequest()
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { controller.save(update, request) }.statusCode)
        authenticate(Role.Names.USER)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> { controller.save(update, request) }.statusCode)
        verify { service wasNot Called }
    }

    @Test
    fun `administrators and superadministrators can submit metadata and named files`() {
        every { service.edit(any(), any()) } just Runs
        val file = MockMultipartFile("cover", byteArrayOf(1))
        val request = MockMultipartHttpServletRequest().apply {
            addFile(file)
            addFile(MockMultipartFile("metadata", byteArrayOf(2)))
        }
        for (role in listOf(Role.Names.ADMIN, Role.Names.SUPERADMIN)) {
            authenticate(role)
            controller.save(update, request)
        }
        verify(exactly = 2) { service.edit(update, mapOf("cover" to file)) }
    }

    @Test
    fun `duplicate upload keys and invalid selections return bad requests`() {
        authenticate(Role.Names.ADMIN)
        val duplicate = MockMultipartHttpServletRequest().apply {
            addFile(MockMultipartFile("cover", byteArrayOf(1)))
            addFile(MockMultipartFile("cover", byteArrayOf(2)))
        }
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> { controller.save(update, duplicate) }.statusCode)
        every { service.edit(any(), any()) } throws IllegalArgumentException("Missing uploaded image: cover")
        assertEquals(HttpStatus.BAD_REQUEST, assertFailsWith<ResponseStatusException> {
            controller.save(update, MockMultipartHttpServletRequest())
        }.statusCode)
    }

    @Test
    fun `multipart HTTP requests deserialize metadata and named files`() {
        authenticate(Role.Names.ADMIN)
        every { service.edit(any(), any()) } just Runs
        val mvc = MockMvcBuilders.standaloneSetup(controller).build()
        val response = mvc.perform(multipart("/game-media/metadata")
            .file(MockMultipartFile("metadata", "metadata", "application/json",
                """{"id":1,"cover":{"uploadKey":"cover"}}""".toByteArray()))
            .file(MockMultipartFile("cover", "cover.png", "image/png", byteArrayOf(1))))
            .andReturn().response
        assertEquals(204, response.status, response.contentAsString)
        verify(exactly = 1) { service.edit(match { it.id == 1L && it.cover?.uploadKey == "cover" }, match { it.keys == setOf("cover") }) }
    }

    @Test
    fun `manual import saves metadata and uploads before removing ignored path`() {
        authenticate(Role.Names.ADMIN)
        val library = mockk<org.gameyfin.app.libraries.entities.Library>()
        val game = mockk<org.gameyfin.app.games.entities.Game> { every { id } returns 42L }
        val metadata = update.copy(title = "Manual title", summary = "Custom summary")
        val file = MockMultipartFile("cover", byteArrayOf(1))
        val request = MockMultipartHttpServletRequest().apply { addFile(file) }
        every { libraries.getWithGamesAndIgnoredPaths(2L) } returns library
        every { service.createManually("Manual title", "/games/manual", library) } returns game
        every { service.edit(any(), any()) } just Runs
        every { libraries.addGamesToLibrary(listOf(game), library, true) } returns library

        controller.importGame(metadata, "/games/manual", 2L, request)

        verifyOrder {
            service.createManually("Manual title", "/games/manual", library)
            service.edit(metadata.copy(id = 42L), mapOf("cover" to file))
            libraries.addGamesToLibrary(listOf(game), library, true)
        }
    }

    @Test
    fun `regular users cannot import games`() {
        authenticate(Role.Names.USER)
        assertEquals(HttpStatus.FORBIDDEN, assertFailsWith<ResponseStatusException> {
            controller.importGame(update, "/games/manual", 2L, MockMultipartHttpServletRequest())
        }.statusCode)
        verify { service wasNot Called; libraries wasNot Called }
    }

    private fun authenticate(role: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("user", "unused", listOf(SimpleGrantedAuthority(role)))
    }
}
