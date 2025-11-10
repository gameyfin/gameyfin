package org.gameyfin.app.core.plugins

import io.mockk.*
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.PluginState
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals

class PluginEndpointTest {

    private lateinit var pluginService: PluginService
    private lateinit var pluginEndpoint: PluginEndpoint

    @BeforeEach
    fun setup() {
        pluginService = mockk()
        pluginEndpoint = PluginEndpoint(pluginService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `subscribe should return flux from PluginService`() {
        val updates = listOf(PluginUpdateDto("plugin1", PluginState.STARTED))

        @Suppress("ReactiveStreamsUnusedPublisher")
        val flux = Flux.just(updates)

        // Mock static method
        mockkObject(PluginService.Companion)
        every { PluginService.subscribe() } returns flux

        val result = pluginEndpoint.subscribe()

        StepVerifier.create(result)
            .expectNext(updates)
            .verifyComplete()

        unmockkObject(PluginService.Companion)
    }

    @Test
    fun `getAll should delegate to pluginService and sort by priority descending`() {
        val plugin1 = createMockPluginDto("plugin1", priority = 5)
        val plugin2 = createMockPluginDto("plugin2", priority = 10)
        val plugin3 = createMockPluginDto("plugin3", priority = 1)
        val plugins = listOf(plugin1, plugin2, plugin3)

        every { pluginService.getAll() } returns plugins

        val result = pluginEndpoint.getAll()

        assertEquals(3, result.size)
        assertEquals("plugin2", result[0].id)
        assertEquals("plugin1", result[1].id)
        assertEquals("plugin3", result[2].id)
        verify(exactly = 1) { pluginService.getAll() }
    }

    @Test
    fun `getAll should return empty list when no plugins`() {
        every { pluginService.getAll() } returns emptyList()

        val result = pluginEndpoint.getAll()

        assertEquals(emptyList(), result)
        verify(exactly = 1) { pluginService.getAll() }
    }

    @Test
    fun `enablePlugin should delegate to pluginService`() {
        val pluginId = "test-plugin"
        every { pluginService.enablePlugin(pluginId) } returns Unit

        pluginEndpoint.enablePlugin(pluginId)

        verify(exactly = 1) { pluginService.enablePlugin(pluginId) }
    }

    @Test
    fun `disablePlugin should delegate to pluginService`() {
        val pluginId = "test-plugin"
        every { pluginService.disablePlugin(pluginId) } returns Unit

        pluginEndpoint.disablePlugin(pluginId)

        verify(exactly = 1) { pluginService.disablePlugin(pluginId) }
    }

    @Test
    fun `setPluginPriorities should delegate to pluginService`() {
        val priorities = mapOf("plugin1" to 10, "plugin2" to 5)
        every { pluginService.setPluginPriorities(priorities) } returns Unit

        pluginEndpoint.setPluginPriorities(priorities)

        verify(exactly = 1) { pluginService.setPluginPriorities(priorities) }
    }

    @Test
    fun `setPluginPriorities should handle empty map`() {
        val priorities = emptyMap<String, Int>()
        every { pluginService.setPluginPriorities(priorities) } returns Unit

        pluginEndpoint.setPluginPriorities(priorities)

        verify(exactly = 1) { pluginService.setPluginPriorities(priorities) }
    }

    @Test
    fun `validatePluginConfig should delegate to pluginService with default flag`() {
        val pluginId = "test-plugin"
        val expectedResult = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)
        every { pluginService.validatePluginConfig(pluginId, true) } returns expectedResult

        val result = pluginEndpoint.validatePluginConfig(pluginId)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { pluginService.validatePluginConfig(pluginId, true) }
    }

    @Test
    fun `validatePluginConfig should return invalid result`() {
        val pluginId = "test-plugin"
        val expectedResult = PluginConfigValidationResult(
            PluginConfigValidationResultType.INVALID,
            mapOf("fieldName" to "Config is invalid")
        )
        every { pluginService.validatePluginConfig(pluginId, true) } returns expectedResult

        val result = pluginEndpoint.validatePluginConfig(pluginId)

        assertEquals(expectedResult, result)
        assertEquals(PluginConfigValidationResultType.INVALID, result.result)
        assertEquals("Config is invalid", result.errors!!["fieldName"])
    }

    @Test
    fun `validateNewConfig should delegate to pluginService`() {
        val pluginId = "test-plugin"
        val config = mapOf("key1" to "value1", "key2" to "value2")
        val expectedResult = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)
        every { pluginService.validatePluginConfig(pluginId, config) } returns expectedResult

        val result = pluginEndpoint.validateNewConfig(pluginId, config)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { pluginService.validatePluginConfig(pluginId, config) }
    }

    @Test
    fun `validateNewConfig should handle empty config map`() {
        val pluginId = "test-plugin"
        val config = emptyMap<String, String>()
        val expectedResult = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)
        every { pluginService.validatePluginConfig(pluginId, config) } returns expectedResult

        val result = pluginEndpoint.validateNewConfig(pluginId, config)

        assertEquals(expectedResult, result)
        verify(exactly = 1) { pluginService.validatePluginConfig(pluginId, config) }
    }

    @Test
    fun `updateConfig should delegate to pluginService`() {
        val pluginId = "test-plugin"
        val config = mapOf("key1" to "value1")
        every { pluginService.updateConfig(pluginId, config) } returns Unit

        pluginEndpoint.updateConfig(pluginId, config)

        verify(exactly = 1) { pluginService.updateConfig(pluginId, config) }
    }

    @Test
    fun `updateConfig should handle multiple config entries`() {
        val pluginId = "test-plugin"
        val config = mapOf(
            "key1" to "value1",
            "key2" to "value2",
            "key3" to "value3"
        )
        every { pluginService.updateConfig(pluginId, config) } returns Unit

        pluginEndpoint.updateConfig(pluginId, config)

        verify(exactly = 1) { pluginService.updateConfig(pluginId, config) }
    }

    private fun createMockPluginDto(
        id: String,
        priority: Int = 1,
        state: PluginState = PluginState.STARTED
    ): PluginDto {
        return PluginDto(
            id = id,
            types = emptyList(),
            name = "Test Plugin",
            description = "Test Description",
            version = "1.0.0",
            author = "Test Author",
            hasLogo = false,
            state = state,
            priority = priority,
            trustLevel = PluginTrustLevel.OFFICIAL
        )
    }
}

