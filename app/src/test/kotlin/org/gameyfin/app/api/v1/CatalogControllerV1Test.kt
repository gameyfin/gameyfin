package org.gameyfin.app.api.v1

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.api.v1.controller.CatalogControllerV1
import org.gameyfin.app.platforms.PlatformService
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CatalogControllerV1Test {
    private val access = allowingAccess()
    private val platforms = mockk<PlatformService>()
    private val controller = CatalogControllerV1(access, platforms)

    @Test
    fun `platforms and metadata options are listed`() {
        val available = Platform.entries.take(2).toSet()
        every { platforms.availablePlatforms } returns available

        val result = controller.platforms()
        assertEquals(available.map { it.name }.sorted(), result.map { it.code })
        assertEquals(available.first { it.name == result.first().code }.displayName, result.first().name)

        val options = controller.options()
        assertEquals(setOf("genres", "themes", "features", "perspectives"), options.keys)
        assertEquals(Genre.entries.size, options.getValue("genres").size)
    }
}
