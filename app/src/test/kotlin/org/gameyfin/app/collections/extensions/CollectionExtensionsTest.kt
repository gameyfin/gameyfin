package org.gameyfin.app.collections.extensions

import io.mockk.every
import io.mockk.mockkStatic
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import kotlin.test.Test
import kotlin.test.assertEquals

class CollectionExtensionsTest {
    @Test
    fun toAdminDtoBuildsStats() {
        val c = Collection(name = "Stats").apply { id = 1L; createdAt = java.time.Instant.now(); updatedAt = createdAt }
        val g1 = Game(library = mockkLibrary(), metadata = GameMetadata(path = "p1")).apply {
            id = 11L; platforms = mutableListOf(org.gameyfin.pluginapi.gamemetadata.Platform.PC_MICROSOFT_WINDOWS)
        }
        val g2 = Game(library = mockkLibrary(), metadata = GameMetadata(path = "p2")).apply {
            id = 12L; platforms = mutableListOf(
            org.gameyfin.pluginapi.gamemetadata.Platform.LINUX,
            org.gameyfin.pluginapi.gamemetadata.Platform.PC_MICROSOFT_WINDOWS
        )
        }
        g1.metadata.downloadCount = 3
        g2.metadata.downloadCount = 7
        c.addGame(g1); c.addGame(g2)

        val dto = c.toAdminDto()
        assertEquals(2, dto.stats?.gamesCount)
        assertEquals(10, dto.stats?.downloadCount)
        assertEquals(
            setOf(
                org.gameyfin.pluginapi.gamemetadata.Platform.PC_MICROSOFT_WINDOWS,
                org.gameyfin.pluginapi.gamemetadata.Platform.LINUX
            ), dto.stats?.gamePlatforms
        )
        assertEquals(listOf(11L, 12L), dto.gameIds)
    }

    @Test
    fun toUserDtoOmitsStatsAndMapsIds() {
        val c = Collection(name = "User").apply { id = 2L; createdAt = java.time.Instant.now(); updatedAt = createdAt }
        val g = Game(library = mockkLibrary(), metadata = GameMetadata(path = "p")).apply { id = 21L }
        c.addGame(g)
        val dto = c.toUserDto()
        assertEquals(listOf(21L), dto.gameIds)
    }

    @Test
    fun toDtoSwitchesByAdminFlag() {
        mockkStatic(::isCurrentUserAdmin)
        val c =
            Collection(name = "Switch").apply { id = 3L; createdAt = java.time.Instant.now(); updatedAt = createdAt }
        every { isCurrentUserAdmin() } returns true
        val admin = c.toDto()
        assertEquals("Switch", admin.name)
        every { isCurrentUserAdmin() } returns false
        val user = c.toDto()
        assertEquals("Switch", user.name)
    }

    private fun mockkLibrary(): org.gameyfin.app.libraries.entities.Library =
        io.mockk.mockk(relaxed = true)
}
