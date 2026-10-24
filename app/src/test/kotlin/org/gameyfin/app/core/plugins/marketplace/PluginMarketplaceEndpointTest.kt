package org.gameyfin.app.core.plugins.marketplace

import io.mockk.*
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.plugins.marketplace.dto.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class PluginMarketplaceEndpointTest {

    private lateinit var service: PluginMarketplaceService
    private lateinit var tasks: PluginInstallationTaskService
    private lateinit var endpoint: PluginMarketplaceEndpoint

    @BeforeEach
    fun setup() {
        service = mockk()
        tasks = mockk()
        endpoint = PluginMarketplaceEndpoint(service, tasks)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun marketplaceDto(id: String = "m1") = PluginMarketplaceDto(
        id = id,
        name = "Marketplace",
        url = "https://github.com/example/marketplace",
        indexUrl = "https://raw.githubusercontent.com/example/marketplace/HEAD/plugins.json",
        enabled = true,
        official = false,
        pluginCount = 3
    )

    private fun marketplacePluginDto() = MarketplacePluginDto(
        id = "plugin",
        marketplaceId = "m1",
        marketplaceName = "Marketplace",
        officialMarketplace = false,
        name = "Plugin",
        hasLogo = false,
        types = emptyList(),
        tags = emptyList(),
        releases = emptyList(),
        status = MarketplacePluginStatus.NOT_INSTALLED
    )

    @Test
    fun `getMarketplaces should delegate to service`() {
        val marketplaces = listOf(marketplaceDto())
        every { service.getMarketplaces() } returns marketplaces

        assertSame(marketplaces, endpoint.getMarketplaces())
        verify(exactly = 1) { service.getMarketplaces() }
    }

    @Test
    fun `addMarketplace should delegate to service`() {
        val create = PluginMarketplaceCreateDto("Marketplace", "https://github.com/example/marketplace")
        val dto = marketplaceDto()
        every { service.addMarketplace(create) } returns dto

        assertSame(dto, endpoint.addMarketplace(create))
        verify(exactly = 1) { service.addMarketplace(create) }
    }

    @Test
    fun `addMarketplace should propagate errors`() {
        val create = PluginMarketplaceCreateDto("", "https://github.com/example/marketplace")
        every { service.addMarketplace(create) } throws IllegalArgumentException("Name must not be empty")

        assertFailsWith<IllegalArgumentException> { endpoint.addMarketplace(create) }
    }

    @Test
    fun `updateMarketplace should delegate to service`() {
        val update = PluginMarketplaceUpdateDto(name = "New name", enabled = false)
        val dto = marketplaceDto()
        every { service.updateMarketplace("m1", update) } returns dto

        assertSame(dto, endpoint.updateMarketplace("m1", update))
        verify(exactly = 1) { service.updateMarketplace("m1", update) }
    }

    @Test
    fun `removeMarketplace should delegate to service`() {
        every { service.removeMarketplace("m1") } just Runs

        endpoint.removeMarketplace("m1")

        verify(exactly = 1) { service.removeMarketplace("m1") }
    }

    @Test
    fun `removeMarketplace should propagate errors`() {
        every { service.removeMarketplace("official") } throws IllegalArgumentException("forbidden")

        assertFailsWith<IllegalArgumentException> { endpoint.removeMarketplace("official") }
    }

    @Test
    fun `getPlugins should delegate to service`() {
        val plugins = listOf(marketplacePluginDto())
        every { service.getPlugins() } returns plugins

        assertSame(plugins, endpoint.getPlugins())
        verify(exactly = 1) { service.getPlugins() }
    }

    @Test
    fun `refresh should delegate to service`() {
        val plugins = listOf(marketplacePluginDto())
        every { service.refresh() } returns plugins

        assertSame(plugins, endpoint.refresh())
        verify(exactly = 1) { service.refresh() }
        verify(exactly = 0) { service.getPlugins() }
    }

    @Test
    fun `install should delegate to service`() {
        val dto = mockk<TaskRunDto>()
        every { tasks.install("m1", "plugin", "1.0.0") } returns dto
        every { tasks.install("m1", "plugin", null) } returns dto

        assertSame(dto, endpoint.install("m1", "plugin", "1.0.0"))
        assertSame(dto, endpoint.install("m1", "plugin", null))
        verify(exactly = 1) { tasks.install("m1", "plugin", "1.0.0") }
        verify(exactly = 1) { tasks.install("m1", "plugin", null) }
    }

    @Test
    fun `install should propagate errors`() {
        every { tasks.install(any(), any(), any()) } throws IllegalStateException("incompatible")

        val e = assertFailsWith<IllegalStateException> { endpoint.install("m1", "plugin", null) }
        assertEquals("incompatible", e.message)
    }

    @Test
    fun `update should delegate to service`() {
        val dto = mockk<TaskRunDto>()
        every { tasks.update("plugin", "2.0.0") } returns dto

        assertSame(dto, endpoint.update("plugin", "2.0.0"))
        verify(exactly = 1) { tasks.update("plugin", "2.0.0") }
    }

    @Test
    fun `uninstall should delegate to service`() {
        val dto = mockk<TaskRunDto>()
        every { tasks.uninstall("plugin") } returns dto

        assertSame(dto, endpoint.uninstall("plugin"))
        verify(exactly = 1) { tasks.uninstall("plugin") }
    }

    @Test
    fun `uninstall should propagate errors`() {
        every { tasks.uninstall("plugin") } throws IllegalArgumentException("not installed")

        assertFailsWith<IllegalArgumentException> { endpoint.uninstall("plugin") }
    }
}
