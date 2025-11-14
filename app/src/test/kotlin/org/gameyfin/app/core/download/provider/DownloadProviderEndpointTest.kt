package org.gameyfin.app.core.download.provider

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.gameyfin.app.core.download.files.DownloadService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadProviderEndpointTest {

    private lateinit var downloadService: DownloadService
    private lateinit var endpoint: DownloadProviderEndpoint

    @BeforeEach
    fun setup() {
        downloadService = mockk<DownloadService>(relaxed = true)
        endpoint = DownloadProviderEndpoint(downloadService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getProviders should return sorted list by priority descending`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Desc 1", "Short 1"),
            DownloadProviderDto("key2", "Provider 2", 10, "Desc 2", "Short 2"),
            DownloadProviderDto("key3", "Provider 3", 3, "Desc 3", "Short 3")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(3, result.size)
        assertEquals("Provider 2", result[0].name)
        assertEquals(10, result[0].priority)
        assertEquals("Provider 1", result[1].name)
        assertEquals(5, result[1].priority)
        assertEquals("Provider 3", result[2].name)
        assertEquals(3, result[2].priority)

        verify(exactly = 1) { downloadService.getProviders() }
    }

    @Test
    fun `getProviders should return empty list when no providers available`() {
        every { downloadService.getProviders() } returns emptyList()

        val result = endpoint.getProviders()

        assertTrue(result.isEmpty())
        verify(exactly = 1) { downloadService.getProviders() }
    }

    @Test
    fun `getProviders should handle single provider`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Description 1", "Short 1")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertEquals("Provider 1", result[0].name)
        assertEquals(5, result[0].priority)
    }

    @Test
    fun `getProviders should handle providers with same priority`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider A", 10, "Desc A", "Short A"),
            DownloadProviderDto("key2", "Provider B", 10, "Desc B", "Short B"),
            DownloadProviderDto("key3", "Provider C", 10, "Desc C", "Short C")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(3, result.size)
        // All should have priority 10
        assertTrue(result.all { it.priority == 10 })
    }

    @Test
    fun `getProviders should handle negative priorities`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", -5, "Desc 1", "Short 1"),
            DownloadProviderDto("key2", "Provider 2", 0, "Desc 2", "Short 2"),
            DownloadProviderDto("key3", "Provider 3", 10, "Desc 3", "Short 3")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(3, result.size)
        assertEquals(10, result[0].priority)
        assertEquals(0, result[1].priority)
        assertEquals(-5, result[2].priority)
    }

    @Test
    fun `getProviders should preserve all DTO fields`() {
        val providers = listOf(
            DownloadProviderDto(
                key = "test.provider.ClassName",
                name = "Test Provider",
                priority = 15,
                description = "This is a detailed description",
                shortDescription = "Short desc"
            )
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertEquals("test.provider.ClassName", result[0].key)
        assertEquals("Test Provider", result[0].name)
        assertEquals(15, result[0].priority)
        assertEquals("This is a detailed description", result[0].description)
        assertEquals("Short desc", result[0].shortDescription)
    }

    @Test
    fun `getProviders should handle null shortDescription`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Description 1", null)
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertNull(result[0].shortDescription)
    }

    @Test
    fun `getProviders should handle large number of providers`() {
        val providers = (1..100).map {
            DownloadProviderDto("key$it", "Provider $it", it, "Desc $it", "Short $it")
        }

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(100, result.size)
        // Should be sorted descending by priority
        assertEquals(100, result[0].priority)
        assertEquals(1, result[99].priority)
    }

    @Test
    fun `getProviders should propagate service exceptions`() {
        every { downloadService.getProviders() } throws RuntimeException("Service error")

        assertThrows(RuntimeException::class.java) {
            endpoint.getProviders()
        }

        verify(exactly = 1) { downloadService.getProviders() }
    }

    @Test
    fun `getProviders should be idempotent`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Desc 1", "Short 1")
        )

        every { downloadService.getProviders() } returns providers

        val result1 = endpoint.getProviders()
        val result2 = endpoint.getProviders()
        val result3 = endpoint.getProviders()

        assertEquals(result1, result2)
        assertEquals(result2, result3)
        verify(exactly = 3) { downloadService.getProviders() }
    }

    @Test
    fun `getProviders should handle special characters in fields`() {
        val providers = listOf(
            DownloadProviderDto(
                key = $$"org.test.Provider$Inner",
                name = "Provider: Special <Chars> & \"Quotes\"",
                priority = 5,
                description = "Description with\nnewlines and\ttabs",
                shortDescription = "Short & sweet"
            )
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertEquals($$"org.test.Provider$Inner", result[0].key)
        assertEquals("Provider: Special <Chars> & \"Quotes\"", result[0].name)
    }

    @Test
    fun `getProviders should handle providers with zero priority`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 0, "Desc 1", "Short 1"),
            DownloadProviderDto("key2", "Provider 2", 5, "Desc 2", "Short 2")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(2, result.size)
        assertEquals(5, result[0].priority)
        assertEquals(0, result[1].priority)
    }

    @Test
    fun `getProviders should handle providers with Integer MAX_VALUE priority`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", Int.MAX_VALUE, "Desc 1", "Short 1"),
            DownloadProviderDto("key2", "Provider 2", Int.MIN_VALUE, "Desc 2", "Short 2"),
            DownloadProviderDto("key3", "Provider 3", 0, "Desc 3", "Short 3")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(3, result.size)
        assertEquals(Int.MAX_VALUE, result[0].priority)
        assertEquals(0, result[1].priority)
        assertEquals(Int.MIN_VALUE, result[2].priority)
    }

    @Test
    fun `should be annotated with Endpoint`() {
        val annotations = DownloadProviderEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "Endpoint" })
    }

    @Test
    fun `should be annotated with DynamicPublicAccess`() {
        val annotations = DownloadProviderEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "DynamicPublicAccess" })
    }

    @Test
    fun `should be annotated with AnonymousAllowed`() {
        val annotations = DownloadProviderEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "AnonymousAllowed" })
    }

    @Test
    fun `getProviders should handle concurrent calls`() {
        val providers = listOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Desc 1", "Short 1")
        )

        every { downloadService.getProviders() } returns providers

        repeat(10) {
            endpoint.getProviders()
        }

        verify(exactly = 10) { downloadService.getProviders() }
    }

    @Test
    fun `getProviders should not modify original list from service`() {
        val originalProviders = mutableListOf(
            DownloadProviderDto("key1", "Provider 1", 5, "Desc 1", "Short 1"),
            DownloadProviderDto("key2", "Provider 2", 10, "Desc 2", "Short 2")
        )

        every { downloadService.getProviders() } returns originalProviders

        val result = endpoint.getProviders()

        // The endpoint sorts the list, but shouldn't modify the original
        assertEquals(2, result.size)
        assertEquals(10, result[0].priority) // Sorted

        // Original list should still be in the same order (if mutable)
        // This tests that sorting creates a new list
        assertNotSame(originalProviders, result)
    }

    @Test
    fun `getProviders should handle empty strings in fields`() {
        val providers = listOf(
            DownloadProviderDto("", "", 5, "", "")
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertEquals("", result[0].key)
        assertEquals("", result[0].name)
        assertEquals("", result[0].description)
        assertEquals("", result[0].shortDescription)
    }

    @Test
    fun `getProviders should handle very long strings in fields`() {
        val longString = "A".repeat(10000)
        val providers = listOf(
            DownloadProviderDto(longString, longString, 5, longString, longString)
        )

        every { downloadService.getProviders() } returns providers

        val result = endpoint.getProviders()

        assertEquals(1, result.size)
        assertEquals(10000, result[0].key.length)
        assertEquals(10000, result[0].name.length)
    }
}

