package org.gameyfin.app.core.plugins.marketplace

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.pf4j.update.FileDownloader
import org.pf4j.update.FileVerifier
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.Instant
import kotlin.test.*

class GameyfinUpdateRepositoryTest {

    private val indexUrl = URI("https://example.com/market/plugins.json")
    private val httpClient = mockk<MarketplaceHttpClient>()
    private val fileDownloader = mockk<FileDownloader>()
    private val fileVerifier = mockk<FileVerifier>()

    private fun index(vararg ids: String) =
        ids.joinToString(",", "[", "]") { """{"id":"$it","releases":[{"version":"1.0.0","url":"$it.jar"}]}""" }

    private fun repository(
        cacheTtl: Duration = Duration.ofHours(1),
        errorRetryInterval: Duration = Duration.ofMinutes(1)
    ) = GameyfinUpdateRepository(
        id = "market",
        indexUrl = indexUrl,
        httpClient = httpClient,
        fileDownloader = fileDownloader,
        fileVerifier = fileVerifier,
        cacheTtl = cacheTtl,
        errorRetryInterval = errorRetryInterval
    )

    private fun stubIndex(vararg responses: String) {
        every { httpClient.getString(indexUrl, MarketplaceHttpClient.MAX_INDEX_SIZE) } returnsMany responses.toList()
    }

    private fun fetchCount(count: Int) {
        verify(exactly = count) { httpClient.getString(indexUrl, MarketplaceHttpClient.MAX_INDEX_SIZE) }
    }

    // --- Basic properties ---

    @Test
    fun `exposes id url downloader and verifier`() {
        val repository = repository()
        assertEquals("market", repository.id)
        assertEquals(indexUrl, repository.indexUrl)
        assertEquals(indexUrl.toURL(), repository.url)
        assertSame(fileDownloader, repository.fileDownloader)
        assertSame(fileVerifier, repository.fileVerifier)
    }

    @Test
    fun `does not fetch the index on construction`() {
        val repository = repository()
        fetchCount(0)
        assertNull(repository.lastFetchedAt)
        assertNull(repository.lastError)
    }

    // --- Fetching and caching ---

    @Test
    fun `fetches the index lazily and parses it`() {
        stubIndex(index("a", "b"))
        val repository = repository()

        val plugins = repository.plugins

        assertEquals(setOf("a", "b"), plugins.keys)
        val plugin = plugins.getValue("a")
        assertIs<GameyfinPluginInfo>(plugin)
        assertEquals("market", plugin.repositoryId)
        assertEquals("https://example.com/market/a.jar", plugin.releases.single().url)
        fetchCount(1)
    }

    @Test
    fun `fetches the index with the index size limit`() {
        stubIndex(index("a"))
        repository().getGameyfinPlugins()
        verify { httpClient.getString(indexUrl, 5L * 1024 * 1024) }
    }

    @Test
    fun `caches the index within the TTL`() {
        stubIndex(index("a"), index("b"))
        val repository = repository()

        repository.plugins
        repository.getPlugin("a")
        repository.getGameyfinPlugins()
        val plugins = repository.plugins

        assertEquals(setOf("a"), plugins.keys)
        fetchCount(1)
    }

    @Test
    fun `refetches the index after the TTL expired`() {
        stubIndex(index("a"), index("b"))
        val repository = repository(cacheTtl = Duration.ZERO)

        assertEquals(setOf("a"), repository.plugins.keys)
        assertEquals(setOf("b"), repository.plugins.keys)
        fetchCount(2)
    }

    @Test
    fun `getPlugin returns single plugin or null`() {
        stubIndex(index("a", "b"))
        val repository = repository()

        assertEquals("b", repository.getPlugin("b")?.id)
        assertNull(repository.getPlugin("missing"))
        fetchCount(1)
    }

    @Test
    fun `refresh forces a refetch on next access`() {
        stubIndex(index("a"), index("b"))
        val repository = repository()

        assertEquals(setOf("a"), repository.plugins.keys)
        repository.refresh()
        fetchCount(1)
        assertEquals(setOf("b"), repository.plugins.keys)
        fetchCount(2)
    }

    @Test
    fun `sets lastFetchedAt on successful fetch`() {
        stubIndex(index("a"))
        val repository = repository()
        val before = Instant.now()

        repository.plugins

        val fetchedAt = assertNotNull(repository.lastFetchedAt)
        assertFalse(fetchedAt.isBefore(before))
        assertFalse(fetchedAt.isAfter(Instant.now()))
    }

    @Test
    fun `lastFetchedAt is not changed by cache hits`() {
        stubIndex(index("a"))
        val repository = repository()

        repository.plugins
        val fetchedAt = repository.lastFetchedAt
        repository.plugins

        assertEquals(fetchedAt, repository.lastFetchedAt)
    }

    // --- Errors ---

    @Test
    fun `returns empty map and sets lastError if the first fetch fails`() {
        every { httpClient.getString(any(), any()) } throws IOException("connection refused")
        val repository = repository()

        assertTrue(repository.plugins.isEmpty())
        assertEquals("connection refused", repository.lastError)
        assertNull(repository.lastFetchedAt)
    }

    @Test
    fun `uses exception class name as lastError if message is missing`() {
        every { httpClient.getString(any(), any()) } throws IllegalStateException()
        val repository = repository()

        repository.plugins

        assertEquals("IllegalStateException", repository.lastError)
    }

    @Test
    fun `invalid index is reported as error`() {
        stubIndex("{\"invalid\":true}")
        val repository = repository()

        assertTrue(repository.plugins.isEmpty())
        assertNotNull(repository.lastError)
    }

    @Test
    fun `keeps the last index if a refetch fails`() {
        every { httpClient.getString(indexUrl, any()) } returns index("a") andThenThrows IOException("server down")
        val repository = repository()

        assertEquals(setOf("a"), repository.plugins.keys)
        val fetchedAt = repository.lastFetchedAt
        repository.refresh()

        assertEquals(setOf("a"), repository.plugins.keys)
        assertEquals("server down", repository.lastError)
        assertEquals(fetchedAt, repository.lastFetchedAt)
        fetchCount(2)
    }

    @Test
    fun `does not retry within the error retry interval`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("server down")
        val repository = repository(cacheTtl = Duration.ZERO)

        repository.plugins
        repository.plugins
        repository.getPlugin("a")

        fetchCount(1)
    }

    @Test
    fun `default error retry interval is one minute`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("server down")
        val repository = GameyfinUpdateRepository("market", indexUrl, httpClient, fileDownloader, fileVerifier, Duration.ZERO)

        repository.plugins
        repository.plugins

        fetchCount(1)
    }

    @Test
    fun `retries after the error retry interval even if the TTL did not expire`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("server down") andThen index("a")
        val repository = repository(cacheTtl = Duration.ofHours(1), errorRetryInterval = Duration.ZERO)

        assertTrue(repository.plugins.isEmpty())
        assertEquals(setOf("a"), repository.plugins.keys)
        fetchCount(2)
    }

    @Test
    fun `successful fetch clears lastError`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("server down") andThen index("a")
        val repository = repository()

        repository.plugins
        assertNotNull(repository.lastError)

        repository.refresh()
        repository.plugins

        assertNull(repository.lastError)
        assertNotNull(repository.lastFetchedAt)
    }

    @Test
    fun `refresh bypasses the error retry interval`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("server down") andThen index("a")
        val repository = repository()

        repository.plugins
        repository.refresh()

        assertEquals(setOf("a"), repository.plugins.keys)
        fetchCount(2)
    }

    // --- fetchOrThrow ---

    @Test
    fun `fetchOrThrow returns the fetched plugins`() {
        stubIndex(index("a", "b"))
        val repository = repository()

        assertEquals(setOf("a", "b"), repository.fetchOrThrow().keys)
    }

    @Test
    fun `fetchOrThrow always refetches`() {
        stubIndex(index("a"), index("b"))
        val repository = repository()

        repository.plugins
        assertEquals(setOf("b"), repository.fetchOrThrow().keys)
        fetchCount(2)
    }

    @Test
    fun `fetchOrThrow throws if fetching fails`() {
        every { httpClient.getString(indexUrl, any()) } throws IOException("HTTP 404")
        val repository = repository()

        val e = assertFailsWith<IllegalArgumentException> { repository.fetchOrThrow() }

        assertTrue(e.message!!.contains(indexUrl.toString()))
        assertTrue(e.message!!.contains("HTTP 404"))
    }

    @Test
    fun `fetchOrThrow throws if refetch fails even if an older index is cached`() {
        every { httpClient.getString(indexUrl, any()) } returns index("a") andThenThrows IOException("server down")
        val repository = repository()

        repository.plugins
        assertFailsWith<IllegalArgumentException> { repository.fetchOrThrow() }
        assertEquals(setOf("a"), repository.plugins.keys)
    }
}
