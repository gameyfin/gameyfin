package org.gameyfin.app.core.plugins

import io.mockk.*
import org.gameyfin.app.core.plugins.config.PluginConfigEntry
import org.gameyfin.app.core.plugins.config.PluginConfigEntryKey
import org.gameyfin.app.core.plugins.config.PluginConfigRepository
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.app.core.plugins.management.*
import org.gameyfin.app.core.plugins.marketplace.GameyfinPluginInfo
import org.gameyfin.app.core.plugins.marketplace.GameyfinUpdateManager
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
import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import reactor.test.StepVerifier
import kotlin.test.*

class PluginServiceTest {

    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var pluginConfigRepository: PluginConfigRepository
    private lateinit var updateManager: GameyfinUpdateManager
    private lateinit var service: PluginService

    @BeforeEach
    fun setup() {
        pluginManager = mockk(relaxed = true)
        pluginManagementRepository = mockk(relaxed = true)
        pluginConfigRepository = mockk(relaxed = true)
        updateManager = mockk(relaxed = true)

        // Default stubs
        every { pluginManager.plugins } returns emptyList()
        every { pluginConfigRepository.findAllByPluginId(any()) } returns emptyList()
        every { pluginManagementRepository.findByIdOrNull(any()) } returns null
        every { updateManager.findPluginInfo(any(), any()) } returns null

        service = PluginService(pluginManager, pluginManagementRepository, pluginConfigRepository, updateManager)
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
    // Validation cache invalidation via plugin state listener
    // -----------------------------------------------------------------------

    private fun createServiceCapturingListener(): Pair<PluginService, CapturingSlot<PluginStateListener>> {
        val listenerSlot = slot<PluginStateListener>()
        every { pluginManager.addPluginStateListener(capture(listenerSlot)) } just Runs
        val serviceWithListener = PluginService(pluginManager, pluginManagementRepository, pluginConfigRepository, updateManager)
        assertTrue(listenerSlot.isCaptured)
        return serviceWithListener to listenerSlot
    }

    @Test
    fun `validation cache should be cleared when plugin is unloaded`() {
        val (serviceWithListener, listenerSlot) = createServiceCapturingListener()
        val pluginId = "unloaded-plugin"
        every { pluginManager.validatePluginConfig(pluginId) } returns
                PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        serviceWithListener.validatePluginConfig(pluginId)
        serviceWithListener.validatePluginConfig(pluginId)
        verify(exactly = 1) { pluginManager.validatePluginConfig(pluginId) }

        val wrapper = mockPluginWrapper(pluginId, PluginState.UNLOADED)
        listenerSlot.captured.pluginStateChanged(PluginStateEvent(pluginManager, wrapper, PluginState.STOPPED))

        serviceWithListener.validatePluginConfig(pluginId)
        verify(exactly = 2) { pluginManager.validatePluginConfig(pluginId) }
    }

    @Test
    fun `validation cache should be kept for state changes other than UNLOADED`() {
        val (serviceWithListener, listenerSlot) = createServiceCapturingListener()
        val pluginId = "stopped-plugin"
        every { pluginManager.validatePluginConfig(pluginId) } returns
                PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        serviceWithListener.validatePluginConfig(pluginId)

        listOf(PluginState.STOPPED, PluginState.STARTED, PluginState.DISABLED, PluginState.FAILED).forEach { state ->
            val wrapper = mockPluginWrapper(pluginId, state)
            listenerSlot.captured.pluginStateChanged(PluginStateEvent(pluginManager, wrapper, PluginState.RESOLVED))
        }

        serviceWithListener.validatePluginConfig(pluginId)
        verify(exactly = 1) { pluginManager.validatePluginConfig(pluginId) }
    }

    @Test
    fun `validation cache should only be cleared for the unloaded plugin`() {
        val (serviceWithListener, listenerSlot) = createServiceCapturingListener()
        every { pluginManager.validatePluginConfig(any()) } returns
                PluginConfigValidationResult(PluginConfigValidationResultType.VALID)

        serviceWithListener.validatePluginConfig("plugin-a")
        serviceWithListener.validatePluginConfig("plugin-b")

        val wrapper = mockPluginWrapper("plugin-a", PluginState.UNLOADED)
        listenerSlot.captured.pluginStateChanged(PluginStateEvent(pluginManager, wrapper, PluginState.STOPPED))

        serviceWithListener.validatePluginConfig("plugin-a")
        serviceWithListener.validatePluginConfig("plugin-b")

        verify(exactly = 2) { pluginManager.validatePluginConfig("plugin-a") }
        verify(exactly = 1) { pluginManager.validatePluginConfig("plugin-b") }
    }

    @Test
    fun `plugin state listener should emit state update`() {
        mockkObject(PluginService.Companion)
        every { PluginService.emit(any()) } just Runs
        val (_, listenerSlot) = createServiceCapturingListener()

        val wrapper = mockPluginWrapper("emitting-plugin", PluginState.UNLOADED)
        listenerSlot.captured.pluginStateChanged(PluginStateEvent(pluginManager, wrapper, PluginState.STOPPED))

        verify(exactly = 1) {
            PluginService.emit(PluginUpdateDto(id = "emitting-plugin", state = PluginState.UNLOADED))
        }
    }

    // -----------------------------------------------------------------------
    // getDto / emitPlugin
    // -----------------------------------------------------------------------

    @Test
    fun `getDto should return dto for loaded plugin`() {
        val wrapper = buildFullPluginWrapper("dto-plugin")
        every { pluginManager.getPlugin("dto-plugin") } returns wrapper

        val result = service.getDto("dto-plugin")

        assertNotNull(result)
        assertEquals("dto-plugin", result.id)
        assertEquals(PluginState.STARTED, result.state)
    }

    @Test
    fun `getDto should return null for unknown plugin`() {
        every { pluginManager.getPlugin("unknown-plugin") } returns null

        assertNull(service.getDto("unknown-plugin"))
        verify(exactly = 0) { pluginManager.getManagementEntry(any()) }
    }

    @Test
    fun `emitPlugin should emit full plugin dto when plugin is loaded`() {
        mockkObject(PluginService.Companion)
        val captured = slot<PluginUpdateDto>()
        every { PluginService.emit(capture(captured)) } just Runs

        val wrapper = buildFullPluginWrapper("emit-plugin")
        every { pluginManager.getPlugin("emit-plugin") } returns wrapper

        service.emitPlugin("emit-plugin")

        verify(exactly = 1) { PluginService.emit(any()) }
        with(captured.captured) {
            assertEquals("emit-plugin", id)
            assertNotNull(plugin)
            assertEquals("emit-plugin", plugin.id)
            assertNull(removed)
            assertNull(state)
        }
    }

    @Test
    fun `emitPlugin should emit removal notice when plugin is not loaded`() {
        mockkObject(PluginService.Companion)
        val captured = slot<PluginUpdateDto>()
        every { PluginService.emit(capture(captured)) } just Runs
        every { pluginManager.getPlugin("removed-plugin") } returns null

        service.emitPlugin("removed-plugin")

        verify(exactly = 1) { PluginService.emit(any()) }
        assertEquals(PluginUpdateDto(id = "removed-plugin", removed = true), captured.captured)
    }

    // -----------------------------------------------------------------------
    // toDto marketplace fields
    // -----------------------------------------------------------------------

    @Test
    fun `toDto should use defaults for local compatible plugin`() {
        val wrapper = buildFullPluginWrapper("local-plugin")
        every { pluginManager.plugins } returns listOf(wrapper)

        val dto = service.getAll().single()

        assertEquals(PluginOrigin.LOCAL, dto.origin)
        assertNull(dto.sourceMarketplaceId)
        assertNull(dto.requires)
        assertTrue(dto.compatible)
        assertNull(dto.failureReason)
    }

    @Test
    fun `toDto should map marketplace origin and source marketplace`() {
        val wrapper = buildFullPluginWrapper(
            "marketplace-plugin",
            origin = PluginOrigin.MARKETPLACE,
            sourceMarketplaceId = "official"
        )
        every { pluginManager.plugins } returns listOf(wrapper)

        val dto = service.getAll().single()

        assertEquals(PluginOrigin.MARKETPLACE, dto.origin)
        assertEquals("official", dto.sourceMarketplaceId)
        verify { pluginManager.getPluginOrigin("marketplace-plugin") }
    }

    @Test
    fun `toDto should fall back to marketplace plugin types when PF4J types are empty`() {
        val wrapper = buildFullPluginWrapper(
            "marketplace-plugin",
            origin = PluginOrigin.MARKETPLACE,
            sourceMarketplaceId = "official"
        )
        every { pluginManager.plugins } returns listOf(wrapper)
        every { pluginManager.getExtensionTypes("marketplace-plugin") } returns emptyList()

        val marketplaceInfo = GameyfinPluginInfo().apply {
            id = "marketplace-plugin"
            types = listOf("GameMetadataProvider")
        }
        every { updateManager.findPluginInfo("marketplace-plugin", "official") } returns marketplaceInfo

        val dto = service.getAll().single()

        assertEquals(listOf("GameMetadataProvider"), dto.types)
        verify(exactly = 1) { updateManager.findPluginInfo("marketplace-plugin", "official") }
    }

    @Test
    fun `toDto should prefer PF4J plugin types over marketplace fallback`() {
        val wrapper = buildFullPluginWrapper(
            "marketplace-plugin",
            origin = PluginOrigin.MARKETPLACE,
            sourceMarketplaceId = "official"
        )
        every { pluginManager.plugins } returns listOf(wrapper)
        every { pluginManager.getExtensionTypes("marketplace-plugin") } returns listOf("DownloadProvider")

        val dto = service.getAll().single()

        assertEquals(listOf("DownloadProvider"), dto.types)
        verify(exactly = 0) { updateManager.findPluginInfo(any(), any()) }
    }

    @Test
    fun `toDto should expose requires constraint`() {
        val wrapper = buildFullPluginWrapper("requires-plugin", requires = ">=2.6.0 & <3.0.0")
        every { pluginManager.plugins } returns listOf(wrapper)

        assertEquals(">=2.6.0 & <3.0.0", service.getAll().single().requires)
    }

    @Test
    fun `toDto should map blank and wildcard requires to null`() {
        listOf("", "   ", "*").forEach { requires ->
            val wrapper = buildFullPluginWrapper("requires-plugin", requires = requires)
            every { pluginManager.plugins } returns listOf(wrapper)

            assertNull(service.getAll().single().requires, "requires '$requires' should be mapped to null")
        }
    }

    @Test
    fun `toDto should map incompatible plugin`() {
        val wrapper = buildFullPluginWrapper("incompatible-plugin", requires = ">=99.0.0", compatible = false)
        every { pluginManager.plugins } returns listOf(wrapper)

        val dto = service.getAll().single()

        assertFalse(dto.compatible)
        assertEquals(">=99.0.0", dto.requires)
        verify { pluginManager.isCompatible(wrapper) }
    }

    @Test
    fun `toDto should map failure reason from failed exception`() {
        val wrapper = buildFullPluginWrapper(
            "failed-plugin",
            state = PluginState.FAILED,
            failedException = IllegalStateException("Something went wrong")
        )
        every { pluginManager.plugins } returns listOf(wrapper)

        val dto = service.getAll().single()

        assertEquals(PluginState.FAILED, dto.state)
        assertEquals("Something went wrong", dto.failureReason)
    }

    @Test
    fun `toDto should map failed exception without message to null failure reason`() {
        val wrapper = buildFullPluginWrapper("failed-plugin", failedException = RuntimeException())
        every { pluginManager.plugins } returns listOf(wrapper)

        assertNull(service.getAll().single().failureReason)
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
        state: PluginState = PluginState.STARTED,
        requires: String? = null,
        origin: PluginOrigin = PluginOrigin.LOCAL,
        compatible: Boolean = true,
        failedException: Throwable? = null,
        sourceMarketplaceId: String? = null
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
        every { descriptor.requires } returns requires
        every { wrapper.descriptor } returns descriptor
        every { wrapper.failedException } returns failedException

        // Non-GameyfinPlugin so hasLogo = false
        every { wrapper.plugin } returns mockk<Plugin>(relaxed = true)

        val managementEntry = PluginManagementEntry(
            pluginId,
            priority = 1,
            trustLevel = PluginTrustLevel.OFFICIAL,
            sourceMarketplaceId = sourceMarketplaceId
        )
        every { pluginManager.getManagementEntry(pluginId) } returns managementEntry
        every { pluginManager.getPluginOrigin(pluginId) } returns origin
        every { pluginManager.isCompatible(wrapper) } returns compatible
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

