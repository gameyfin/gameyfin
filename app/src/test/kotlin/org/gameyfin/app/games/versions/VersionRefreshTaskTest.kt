package org.gameyfin.app.games.versions

import io.mockk.*
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.gameyfin.pluginapi.tasks.TaskContext
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionRefreshTaskTest {
    private val registry = mockk<TaskRegistry>(relaxed = true)
    private val links = mockk<GameStoreLinkRepository>()
    private val cache = mockk<StoreVersionCacheService>()
    private val context = mockk<TaskContext>(relaxed = true)

    private fun task(vararg stores: ActiveGameStore) =
        VersionRefreshTask(registry, links, cache, registryOf(*stores))

    @Test
    fun `refreshes and caches versions for every link`() {
        val gog = mockk<GameStoreProvider>()
        val versions = listOf(GameStoreVersion("build-1", label = "1.0"))
        every { gog.fetchVersions("123") } returns versions
        every { links.findAll() } returns listOf(
            GameStoreLink(id = 1, gameId = 10, store = "gog", storeAppId = "123")
        )
        every { cache.update(1, versions, any()) } just Runs

        val result = task(activeStore(GOG_INFO, gog)).execute(context)

        assertEquals("Refreshed versions for 1 store link(s)", result.summary)
        verify { cache.update(1, versions, any()) }
        verify { context.throwIfCancelled() }
    }

    @Test
    fun `refreshes a single link on demand`() {
        val steam = mockk<GameStoreProvider>()
        val versions = listOf(GameStoreVersion("build-2", label = "2.0"))
        every { steam.fetchVersions("100") } returns versions
        every { links.findById(5) } returns java.util.Optional.of(
            GameStoreLink(id = 5, gameId = 10, store = "steam", storeAppId = "100")
        )
        every { cache.update(5, versions, any()) } just Runs

        task(activeStore(STEAM_INFO, steam)).refreshLink(5)

        verify { cache.update(5, versions, any()) }
    }

    @Test
    fun `refreshing an unknown link fails`() {
        every { links.findById(99) } returns java.util.Optional.empty()

        assertFailsWith<NoSuchElementException> { task().refreshLink(99) }
    }

    @Test
    fun `reports failed refresh as task failure and preserves other link processing`() {
        val steam = mockk<GameStoreProvider>()
        val gog = mockk<GameStoreProvider>()
        val versions = listOf(GameStoreVersion("gog-build", label = "1.0"))
        every { steam.fetchVersions("100") } throws IllegalStateException("source unavailable")
        every { gog.fetchVersions("200") } returns versions
        every { links.findAll() } returns listOf(
            GameStoreLink(id = 1, gameId = 10, store = "steam", storeAppId = "100"),
            GameStoreLink(id = 2, gameId = 10, store = "gog", storeAppId = "200")
        )
        every { cache.update(2, versions, any()) } just Runs

        val error = assertFailsWith<IllegalStateException> { task(activeStore(STEAM_INFO, steam), activeStore(GOG_INFO, gog)).execute(context) }

        assertEquals("Version refresh updated 1 of 2 links; 1 failed", error.message)
        verify { cache.update(2, versions, any()) }
        verify(exactly = 0) { cache.update(1, any(), any()) }
    }

    @Test
    fun `skips links whose store plugin is not enabled without failing`() {
        val gog = mockk<GameStoreProvider>()
        val versions = listOf(GameStoreVersion("gog-build", label = "1.0"))
        every { gog.fetchVersions("200") } returns versions
        every { links.findAll() } returns listOf(
            GameStoreLink(id = 1, gameId = 10, store = "steam", storeAppId = "100"),
            GameStoreLink(id = 2, gameId = 10, store = "gog", storeAppId = "200")
        )
        every { cache.update(2, versions, any()) } just Runs

        val result = task(activeStore(GOG_INFO, gog)).execute(context)

        assertEquals("Refreshed versions for 1 store link(s) (1 skipped because their store plugin is not enabled)", result.summary)
        verify(exactly = 0) { cache.update(1, any(), any()) }
    }

    @Test
    fun `does nothing when no game store plugin is enabled`() {
        every { links.findAll() } returns listOf(GameStoreLink(id = 1, gameId = 10, store = "gog", storeAppId = "200"))

        val result = task().execute(context)

        assertEquals("Skipped: no game store plugin is enabled", result.summary)
        verify(exactly = 0) { cache.update(any(), any(), any()) }
    }

    @Test
    fun `refreshing a link of a disabled store plugin fails`() {
        every { links.findById(5) } returns java.util.Optional.of(
            GameStoreLink(id = 5, gameId = 10, store = "steam", storeAppId = "100")
        )

        assertFailsWith<StoreUnavailableException> { task().refreshLink(5) }
    }
}
