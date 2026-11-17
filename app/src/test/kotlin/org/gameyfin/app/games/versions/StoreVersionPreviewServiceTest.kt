package org.gameyfin.app.games.versions

import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoreVersionPreviewServiceTest {
    private val provider = mockk<GameStoreProvider>()
    private val statusService = mockk<GameVersionStatusService>()
    private val service = StoreVersionPreviewService(registryOf(activeStore(GOG_INFO, provider)), statusService)

    private val versions = listOf(
        GameStoreVersion("b2", "", "2.0"),
        GameStoreVersion("b1", "", "1.0")
    )

    @Test
    fun `returns fetched versions and the index of the detected one`() {
        every { provider.fetchVersions("987") } returns versions
        every { statusService.detectInstalledValues(10, "gog", "987") } returns listOf("b1")

        val result = service.preview(10, "gog", "https://www.gogdb.org/product/987")

        assertEquals(listOf("2.0", "1.0"), result.versions.map { it.label })
        assertEquals(1, result.detectedIndex)
    }

    @Test
    fun `detection problems do not hide the versions`() {
        every { provider.fetchVersions("987") } returns versions
        every { statusService.detectInstalledValues(10, "gog", "987") } throws IllegalStateException("boom")

        val result = service.preview(10, "gog", "987")

        assertEquals(2, result.versions.size)
        assertNull(result.detectedIndex)
    }

    @Test
    fun `rejects invalid IDs without contacting the store`() {
        assertFailsWith<IllegalArgumentException> { service.preview(10, "gog", "nope") }
        verify(exactly = 0) { provider.fetchVersions(any()) }
    }

    @Test
    fun `fails when no active plugin provides the store`() {
        assertFailsWith<StoreUnavailableException> { service.preview(10, "steam", "123") }
    }
}
