package org.gameyfin.app.games.uploads

import io.mockk.*
import org.gameyfin.app.core.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID
import kotlin.test.assertEquals

class GameUploadControllerTest {
    private val service = mockk<GameUploadService>(relaxed = true)
    private val mvc = MockMvcBuilders.standaloneSetup(GameUploadController(service)).build()

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    private fun authenticate(role: String) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "admin", "", listOf(SimpleGrantedAuthority(role))
        )
    }

    @Test
    fun `every upload operation denies anonymous and regular users`() {
        val id = UUID.randomUUID()
        for (role in listOf(null, Role.Names.USER)) {
            if (role == null) SecurityContextHolder.clearContext() else authenticate(role)
            val requests = listOf(
                post("/game-uploads/destination").contentType("application/json").content(
                    """{"libraryId":1,"directory":"/games","name":"game.iso"}"""
                ),
                post("/game-uploads").contentType("application/json").content(
                    """{"libraryId":1,"directory":"/games","name":"game.iso","folder":false,"files":[{"path":"game.iso","size":3}]}"""
                ),
                post("/game-uploads/$id/metadata").contentType("application/json").content("{}"),
                post("/game-uploads/$id/restart"),
                put("/game-uploads/$id/files/0").contentType("application/octet-stream").content("abc"),
                multipart("/game-uploads/$id/complete").file(MockMultipartFile(
                    "metadata", "metadata", "application/json", """{"id":0,"title":"Game"}""".toByteArray()
                )),
                get("/game-uploads/$id"), delete("/game-uploads/$id")
            )
            requests.forEach { assertEquals(403, mvc.perform(it).andReturn().response.status) }
        }
        verify { service wasNot Called }
    }

    @Test
    fun `admins and superadmins can create sessions transfer binary and publish metadata artwork`() {
        val id = UUID.randomUUID()
        every { service.create("admin", any()) } returns GameUploadStatus(id, null, 0)
        every { service.complete("admin", id, any(), any()) } returns GameUploadStatus(id, 42, 1)
        for (role in listOf(Role.Names.ADMIN, Role.Names.SUPERADMIN)) {
            authenticate(role)
            assertEquals(204, mvc.perform(post("/game-uploads/destination").contentType("application/json").content(
                """{"libraryId":1,"directory":"/games","name":"game.iso"}"""
            )).andReturn().response.status)
            assertEquals(200, mvc.perform(post("/game-uploads").contentType("application/json").content(
                """{"libraryId":1,"directory":"/games","name":"game.iso","folder":false,"files":[{"path":"game.iso","size":3}]}"""
            )).andReturn().response.status)
            assertEquals(204, mvc.perform(put("/game-uploads/$id/files/0")
                .contentType("application/octet-stream").content("abc")).andReturn().response.status)
            assertEquals(200, mvc.perform(multipart("/game-uploads/$id/complete")
                .file(MockMultipartFile("metadata", "metadata", "application/json",
                    """{"id":0,"title":"Game","cover":{"uploadKey":"cover"}}""".toByteArray()))
                .file(MockMultipartFile("cover", "cover.png", "image/png", byteArrayOf(1))))
                .andReturn().response.status)
        }
        verify(exactly = 2) {
            service.complete("admin", id, match { it.title == "Game" && it.cover?.uploadKey == "cover" },
                match { it.keys == setOf("cover") })
        }
    }

    @Test
    fun `duplicate artwork parts are rejected before publication`() {
        authenticate(Role.Names.ADMIN)
        val id = UUID.randomUUID()
        assertEquals(400, mvc.perform(multipart("/game-uploads/$id/complete")
            .file(MockMultipartFile("metadata", "metadata", "application/json", """{"id":0}""".toByteArray()))
            .file(MockMultipartFile("cover", byteArrayOf(1)))
            .file(MockMultipartFile("cover", byteArrayOf(2))))
            .andReturn().response.status)
        verify { service wasNot Called }
    }
}
