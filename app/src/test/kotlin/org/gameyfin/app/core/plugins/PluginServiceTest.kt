package org.gameyfin.app.core.plugins

import io.mockk.*
import org.gameyfin.app.core.plugins.config.PluginConfigEntry
import org.gameyfin.app.core.plugins.config.PluginConfigEntryKey
import org.gameyfin.app.core.plugins.config.PluginConfigRepository
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.app.core.plugins.management.*
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.Configurable
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginState
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import reactor.test.StepVerifier
import kotlin.test.*

class PluginServiceTest {

    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var pluginConfigRepository: PluginConfigRepository
    private lateinit var service: PluginService

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        pluginManagementRepository = mockk(relaxed = true)
        pluginConfigRepository = mockk(relaxed = true)

        // Default stubs
        every { pluginManager.plugins } returns emptyList()
        every { pluginConfigRepository.findAllByPluginId(any()) } returns emptyList()
        every { pluginManagementRepository.findByIdOrNull(any()) } returns null

        service = PluginService(pluginManager, pluginManagementRepository, pluginConfigRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    // -----------------------------------------------------------------------
    // subscribe / emit
    // -----------------------------------------------------------------------

    @Test
    fun `subscribe should return a flux that receives emitted updates`() {
        val update = PluginUpdateDto("plugin1", PluginState.STARTED)

        val flux = PluginService.subscribe()
        PluginService.emit(update)

        StepVerifier.create(flux.take(1))
            .assertNext { batch -> assertTrue(batch.contains(update)) }
            .verifyComplete()
    }

    // -----------------------------------------------------------------------
    // getSupportedPluginTypes
    // -----------------------------------------------------------------------

    @Test
    fun `getSupportedPluginTypes should aggregate extension types from all plugins`() {
        val wrapper1 = mockPluginWrapper("p1")
        val wrapper2 = mockPluginWrapper("p2")

        every { pluginManager.plugins } returns listOf(wrapper1, wrapper2)
        every { pluginManager.getExtensionTypes("p1") } returns listOf("DownloadProvider")
        every { pluginManager.getExtensionTypes("p2") } returns listOf("GameMetadataProvider")

        val result = service.getSupportedPluginTypes()

        assertEquals(listOf("DownloadProvider", "GameMetadataProvider"), result)
    }

    @Test
    fun `getSupportedPluginTypes should return empty list when no plugins are registered`() {
        every { pluginManager.plugins } returns emptyList()

        val result = service.getSupportedPluginTypes()

        assertTrue(result.isEmpty())
    }

    // -----------------------------------------------------------------------
    // getAll
    // -----------------------------------------------------------------------

    @Test
    fun `getAll should return a PluginDto for every loaded plugin`() {
        val wrapper = buildFullPluginWrapper("my-plugin")
        every { pluginManager.plugins } returns listOf(wrapper)

        val result = service.getAll()

        assertEquals(1, result.size)
        assertEquals("my-plugin", result[0].id)
    }

    @Test
    fun `getAll should return empty list when no plugins are loaded`() {
        every { pluginManager.plugins } returns emptyList()

        assertTrue(service.getAll().isEmpty())
    }

    // -----------------------------------------------------------------------
    // getAllByTypeAndState
    // -----------------------------------------------------------------------

    @Test
    fun `getAllByTypeAndState should only return plugins matching the given state`() {
        val startedWrapper = buildFullPluginWrapper("started-plugin", PluginState.STARTED)
        val stoppedWrapper = buildFullPluginWrapper("stopped-plugin", PluginState.STOPPED)

        every { pluginManager.getPluginsForExtension(GameMetadataProviderStub::class) } returns
                listOf(startedWrapper, stoppedWrapper)

        val result = service.getAllByTypeAndState(GameMetadataProviderStub::class, PluginState.STARTED)

        assertEquals(1, result.size)
        assertEquals("started-plugin", result[0].id)
    }

    @Test
    fun `getAllByTypeAndState should return empty list when no plugins match`() {
        every { pluginManager.getPluginsForExtension(GameMetadataProviderStub::class) } returns emptyList()

        val result = service.getAllByTypeAndState(GameMetadataProviderStub::class, PluginState.STARTED)

        assertTrue(result.isEmpty())
    }

    // -----------------------------------------------------------------------
    // getPluginManagementEntry
    // -----------------------------------------------------------------------

    @Test
    fun `getPluginManagementEntry should return entry for the plugin owning the class`() {
        val wrapper = mockPluginWrapper("owner-plugin")
        val entry = PluginManagementEntry("owner-plugin", enabled = true)

        every { pluginManager.whichPlugin(GameMetadataProviderStub::class.java) } returns wrapper
        every { pluginManagementRepository.findByIdOrNull("owner-plugin") } returns entry

        val result = service.getPluginManagementEntry(GameMetadataProviderStub::class.java)

        assertEquals(entry, result)
    }

    @Test
    fun `getPluginManagementEntry should throw when no management entry exists`() {
        val wrapper = mockPluginWrapper("missing-plugin")
        every { pluginManager.whichPlugin(GameMetadataProviderStub::class.java) } returns wrapper
        every { pluginManagementRepository.findByIdOrNull("missing-plugin") } returns null

        assertFailsWith<IllegalArgumentException> {
            service.getPluginManagementEntry(GameMetadataProviderStub::class.java)
        }
    }

    // -----------------------------------------------------------------------
    // getPluginManagementEntries
    // -----------------------------------------------------------------------

    @Test
    fun `getPluginManagementEntries should return entries for started plugins with matching type`() {
        val wrapper = buildFullPluginWrapper("started-plugin", PluginState.STARTED)
        val entry = PluginManagementEntry("started-plugin", enabled = true)

        every { pluginManager.plugins } returns listOf(wrapper)
        every { pluginManager.getExtensionTypes("started-plugin") } returns listOf("GameMetadataProviderStub")
        every { pluginManagementRepository.findByIdOrNull("started-plugin") } returns entry

        val result = service.getPluginManagementEntries(GameMetadataProviderStub::class.java, enabledOnly = true)

        assertEquals(1, result.size)
        assertEquals(entry, result[0])
    }

    @Test
    fun `getPluginManagementEntries with enabledOnly false should include non-started plugins`() {
        val stoppedWrapper = buildFullPluginWrapper("stopped-plugin", PluginState.STOPPED)
        val entry = PluginManagementEntry("stopped-plugin", enabled = false)

        every { pluginManager.plugins } returns listOf(stoppedWrapper)
        every { pluginManager.getExtensionTypes("stopped-plugin") } returns listOf("GameMetadataProviderStub")
        every { pluginManagementRepository.findByIdOrNull("stopped-plugin") } returns entry

        val result = service.getPluginManagementEntries(GameMetadataProviderStub::class.java, enabledOnly = false)

        assertEquals(1, result.size)
        assertEquals(entry, result[0])
    }

    @Test
    fun `getPluginManagementEntries should throw when management entry is missing`() {
        val wrapper = buildFullPluginWrapper("no-entry-plugin", PluginState.STARTED)

        every { pluginManager.plugins } returns listOf(wrapper)
        every { pluginManager.getExtensionTypes("no-entry-plugin") } returns listOf("GameMetadataProviderStub")
        every { pluginManagementRepository.findByIdOrNull("no-entry-plugin") } returns null

        assertFailsWith<IllegalArgumentException> {
            service.getPluginManagementEntries(GameMetadataProviderStub::class.java, enabledOnly = true)
        }
    }

    // -----------------------------------------------------------------------
    // enablePlugin / disablePlugin
    // -----------------------------------------------------------------------

    @Test
    fun `enablePlugin should delegate to pluginManager`() {
        service.enablePlugin("my-plugin")
        verify(exactly = 1) { pluginManager.enablePlugin("my-plugin") }
    }

    @Test
    fun `disablePlugin should delegate to pluginManager`() {
        service.disablePlugin("my-plugin")
        verify(exactly = 1) { pluginManager.disablePlugin("my-plugin") }
    }

    // -----------------------------------------------------------------------
    // setPluginPriorities
    // -----------------------------------------------------------------------

    @Test
    fun `setPluginPriorities should persist updated priorities for each plugin`() {
        val entry1 = PluginManagementEntry("p1", priority = 1)
        val entry2 = PluginManagementEntry("p2", priority = 2)

        every { pluginManager.getManagementEntry("p1") } returns entry1
        every { pluginManager.getManagementEntry("p2") } returns entry2
        every { pluginManagementRepository.save(any()) } returnsArgument 0

        service.setPluginPriorities(mapOf("p1" to 10, "p2" to 5))

        verify(exactly = 1) { pluginManagementRepository.save(match { it.pluginId == "p1" && it.priority == 10 }) }
        verify(exactly = 1) { pluginManagementRepository.save(match { it.pluginId == "p2" && it.priority == 5 }) }
    }

    @Test
    fun `setPluginPriorities with empty map should not call save`() {
        service.setPluginPriorities(emptyMap())
        verify(exactly = 0) { pluginManagementRepository.save(any()) }
    }

    // -----------------------------------------------------------------------
    // getLogo
    // -----------------------------------------------------------------------

    @Test
    fun `getLogo should return bytes from plugin`() {
        val logoBytes = byteArrayOf(1, 2, 3)
        val gameyfinPlugin = mockk<GameyfinPlugin>()
        val wrapper = mockPluginWrapper("logo-plugin")

        every { wrapper.plugin } returns gameyfinPlugin
        every { pluginManager.getPlugin("logo-plugin") } returns wrapper
        every { gameyfinPlugin.getLogo() } returns logoBytes

        val result = service.getLogo("logo-plugin")

        assertContentEquals(logoBytes, result)
    }

    @Test
    fun `getLogo should return null when plugin has no logo`() {
        val gameyfinPlugin = mockk<GameyfinPlugin>()
        val wrapper = mockPluginWrapper("no-logo-plugin")

        every { wrapper.plugin } returns gameyfinPlugin
        every { pluginManager.getPlugin("no-logo-plugin") } returns wrapper
        every { gameyfinPlugin.getLogo() } returns null

        assertNull(service.getLogo("no-logo-plugin"))
    }

    // -----------------------------------------------------------------------
    // getConfigMetadata
    // -----------------------------------------------------------------------

    @Test
    fun `getConfigMetadata should return null for non-configurable plugin`() {
        val wrapper = mockPluginWrapper("plain-plugin")
        every { wrapper.plugin } returns mockk<Plugin>(relaxed = true)

        assertNull(service.getConfigMetadata(wrapper))
    }

    @Test
    fun `getConfigMetadata should return metadata for configurable plugin`() {
        val meta = ConfigMetadata(
            key = "apiKey",
            type = String::class.java,
            label = "API Key",
            description = "Your API key",
            isSecret = true,
            isRequired = true,
        )

        val configurablePlugin = mockk<TestConfigurablePlugin>()
        every { configurablePlugin.configMetadata } returns listOf(meta)

        val wrapper = mockPluginWrapper("configurable-plugin")
        every { wrapper.plugin } returns configurablePlugin

        val result = service.getConfigMetadata(wrapper)

        assertNotNull(result)
        assertEquals(1, result.size)
        with(result[0]) {
            assertEquals("apiKey", key)
            assertEquals("String", type)
            assertEquals("API Key", label)
            assertEquals("Your API key", description)
            assertTrue(secret)
            assertTrue(required)
            assertNull(allowedValues)
        }
    }

    @Test
    fun `getConfigMetadata should include allowed values for enum-typed config entries`() {
        val meta = ConfigMetadata(
            key = "mode",
            type = TestMode::class.java,
            label = "Mode",
            description = "Operation mode",
        )

        val configurablePlugin = mockk<TestConfigurablePlugin>()
        every { configurablePlugin.configMetadata } returns listOf(meta)

        val wrapper = mockPluginWrapper("enum-config-plugin")
        every { wrapper.plugin } returns configurablePlugin

        val result = service.getConfigMetadata(wrapper)

        assertNotNull(result)
        assertEquals(listOf("FAST", "SLOW"), result[0].allowedValues)
    }

    // -----------------------------------------------------------------------
    // getConfig
    // -----------------------------------------------------------------------

    @Test
    fun `getConfig should return key-value map from repository`() {
        val wrapper = mockPluginWrapper("cfg-plugin")
        val entries = listOf(
            PluginConfigEntry(PluginConfigEntryKey("cfg-plugin", "host"), "localhost"),
            PluginConfigEntry(PluginConfigEntryKey("cfg-plugin", "port"), "8080")
        )
        every { pluginConfigRepository.findAllByPluginId("cfg-plugin") } returns entries

        val result = service.getConfig(wrapper)

        assertEquals(mapOf("host" to "localhost", "port" to "8080"), result)
    }

    @Test
    fun `getConfig should return empty map when no config entries exist`() {
        val wrapper = mockPluginWrapper("empty-cfg-plugin")
        every { pluginConfigRepository.findAllByPluginId("empty-cfg-plugin") } returns emptyList()

        assertTrue(service.getConfig(wrapper).isEmpty())
    }

    // -----------------------------------------------------------------------
    // updateConfig
    // -----------------------------------------------------------------------

    @Test
    fun `updateConfig should persist entries, restart plugin, validate and emit update`() {
        val pluginId = "upd-plugin"
        val config = mapOf("key1" to "val1")
        val validationResult = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        every { pluginConfigRepository.saveAll(any<List<PluginConfigEntry>>()) } returns emptyList()
        every { pluginManager.restart(pluginId) } just Runs
        every { pluginManager.validatePluginConfig(pluginId) } returns validationResult

        mockkObject(PluginService.Companion)
        every { PluginService.emit(any()) } just Runs

        assertDoesNotThrow { service.updateConfig(pluginId, config) }

        verify(exactly = 1) { pluginConfigRepository.saveAll(any<List<PluginConfigEntry>>()) }
        verify(exactly = 1) { pluginManager.restart(pluginId) }
        verify(exactly = 1) { PluginService.emit(match { it.id == pluginId && it.config == config }) }

        unmockkObject(PluginService.Companion)
    }

    // -----------------------------------------------------------------------
    // validatePluginConfig (by pluginId)
    // -----------------------------------------------------------------------

    @Test
    fun `validatePluginConfig should return cached result on second call`() {
        val pluginId = "cache-plugin"
        val result = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        every { pluginManager.validatePluginConfig(pluginId) } returns result

        service.validatePluginConfig(pluginId)
        service.validatePluginConfig(pluginId)

        // Second call should use cache – manager is called only once
        verify(exactly = 1) { pluginManager.validatePluginConfig(pluginId) }
    }

    @Test
    fun `validatePluginConfig with forceRevalidation should bypass cache`() {
        val pluginId = "force-plugin"
        val result = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        every { pluginManager.validatePluginConfig(pluginId) } returns result

        service.validatePluginConfig(pluginId, forceRevalidation = false)
        service.validatePluginConfig(pluginId, forceRevalidation = true)

        verify(exactly = 2) { pluginManager.validatePluginConfig(pluginId) }
    }

    @Test
    fun `validatePluginConfig should return INVALID result when config is invalid`() {
        val pluginId = "invalid-plugin"
        val result = PluginConfigValidationResult(
            PluginConfigValidationResultType.INVALID,
            mapOf("apiKey" to "Required")
        )
        every { pluginManager.validatePluginConfig(pluginId) } returns result

        val actual = service.validatePluginConfig(pluginId)

        assertEquals(PluginConfigValidationResultType.INVALID, actual.result)
        assertEquals("Required", actual.errors?.get("apiKey"))
    }

    // -----------------------------------------------------------------------
    // validatePluginConfig (with config map)
    // -----------------------------------------------------------------------

    @Test
    fun `validatePluginConfig with config map should delegate to pluginManager`() {
        val pluginId = "map-plugin"
        val config = mapOf("key" to "value")
        val result = PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        every { pluginManager.validatePluginConfig(pluginId, config) } returns result

        val actual = service.validatePluginConfig(pluginId, config)

        assertEquals(result, actual)
        verify(exactly = 1) { pluginManager.validatePluginConfig(pluginId, config) }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Creates a minimal PluginWrapper mock with a given ID and state. */
    private fun mockPluginWrapper(pluginId: String, state: PluginState = PluginState.STARTED): PluginWrapper {
        val wrapper = mockk<PluginWrapper>(relaxed = true)
        every { wrapper.pluginId } returns pluginId
        every { wrapper.pluginState } returns state
        return wrapper
    }

    /**
     * Builds a PluginWrapper mock that satisfies the full toDto() path inside PluginService,
     * including descriptor, management entry, and validation.
     */
    private fun buildFullPluginWrapper(
        pluginId: String,
        state: PluginState = PluginState.STARTED
    ): PluginWrapper {
        val wrapper = mockPluginWrapper(pluginId, state)

        val descriptor = mockk<GameyfinPluginDescriptor>()
        every { descriptor.pluginId } returns pluginId
        every { descriptor.pluginName } returns "Test Plugin"
        every { descriptor.pluginDescription } returns "Description"
        every { descriptor.pluginShortDescription } returns null
        every { descriptor.version } returns "1.0.0"
        every { descriptor.author } returns "Author"
        every { descriptor.license } returns null
        every { descriptor.pluginUrl } returns null
        every { wrapper.descriptor } returns descriptor

        // Non-GameyfinPlugin so hasLogo = false
        every { wrapper.plugin } returns mockk<Plugin>(relaxed = true)

        val managementEntry = PluginManagementEntry(pluginId, priority = 1, trustLevel = PluginTrustLevel.OFFICIAL)
        every { pluginManager.getManagementEntry(pluginId) } returns managementEntry
        every { pluginManager.getExtensionTypes(pluginId) } returns emptyList()
        every { pluginConfigRepository.findAllByPluginId(pluginId) } returns emptyList()
        every { pluginManager.validatePluginConfig(pluginId) } returns
                PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        return wrapper
    }

    // Stub types used in tests
    interface GameMetadataProviderStub : ExtensionPoint

    @Suppress("deprecation", "redundantSuppression")
    abstract class TestConfigurablePlugin : Plugin(mockk(relaxed = true)), Configurable

    @Suppress("unused")
    enum class TestMode { FAST, SLOW }
}

