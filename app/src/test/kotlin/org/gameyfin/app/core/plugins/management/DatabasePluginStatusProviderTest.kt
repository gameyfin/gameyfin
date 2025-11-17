package org.gameyfin.app.core.plugins.management

import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabasePluginStatusProviderTest {

    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var statusProvider: DatabasePluginStatusProvider

    @BeforeEach
    fun setup() {
        pluginManagementRepository = mockk()
        statusProvider = DatabasePluginStatusProvider(pluginManagementRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isPluginDisabled should return true when plugin is disabled`() {
        val entry = PluginManagementEntry("test-plugin", enabled = false, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry

        val result = statusProvider.isPluginDisabled("test-plugin")

        assertTrue(result)
    }

    @Test
    fun `isPluginDisabled should return false when plugin is enabled`() {
        val entry = PluginManagementEntry("test-plugin", enabled = true, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry

        val result = statusProvider.isPluginDisabled("test-plugin")

        assertFalse(result)
    }

    @Test
    fun `isPluginDisabled should return true when plugin entry not found`() {
        every { pluginManagementRepository.findByIdOrNull("unknown-plugin") } returns null

        val result = statusProvider.isPluginDisabled("unknown-plugin")

        assertTrue(result)
    }

    @Test
    fun `disablePlugin should set enabled to false and save`() {
        val entry = PluginManagementEntry("test-plugin", enabled = true, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry
        every { pluginManagementRepository.save(entry) } returns entry

        statusProvider.disablePlugin("test-plugin")

        assertFalse(entry.enabled)
        verify(exactly = 1) { pluginManagementRepository.save(entry) }
    }

    @Test
    fun `disablePlugin should do nothing when plugin entry not found`() {
        every { pluginManagementRepository.findByIdOrNull("unknown-plugin") } returns null

        statusProvider.disablePlugin("unknown-plugin")

        verify(exactly = 0) { pluginManagementRepository.save(any()) }
    }

    @Test
    fun `disablePlugin should handle already disabled plugin`() {
        val entry = PluginManagementEntry("test-plugin", enabled = false, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry
        every { pluginManagementRepository.save(entry) } returns entry

        statusProvider.disablePlugin("test-plugin")

        assertFalse(entry.enabled)
        verify(exactly = 1) { pluginManagementRepository.save(entry) }
    }

    @Test
    fun `enablePlugin should set enabled to true and save`() {
        val entry = PluginManagementEntry("test-plugin", enabled = false, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry
        every { pluginManagementRepository.save(entry) } returns entry

        statusProvider.enablePlugin("test-plugin")

        assertTrue(entry.enabled)
        verify(exactly = 1) { pluginManagementRepository.save(entry) }
    }

    @Test
    fun `enablePlugin should do nothing when plugin entry not found`() {
        every { pluginManagementRepository.findByIdOrNull("unknown-plugin") } returns null

        statusProvider.enablePlugin("unknown-plugin")

        verify(exactly = 0) { pluginManagementRepository.save(any()) }
    }

    @Test
    fun `enablePlugin should handle already enabled plugin`() {
        val entry = PluginManagementEntry("test-plugin", enabled = true, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry
        every { pluginManagementRepository.save(entry) } returns entry

        statusProvider.enablePlugin("test-plugin")

        assertTrue(entry.enabled)
        verify(exactly = 1) { pluginManagementRepository.save(entry) }
    }

    @Test
    fun `should handle multiple enable and disable calls`() {
        val entry = PluginManagementEntry("test-plugin", enabled = false, priority = 1)
        every { pluginManagementRepository.findByIdOrNull("test-plugin") } returns entry
        every { pluginManagementRepository.save(entry) } returns entry

        statusProvider.enablePlugin("test-plugin")
        assertTrue(entry.enabled)

        statusProvider.disablePlugin("test-plugin")
        assertFalse(entry.enabled)

        statusProvider.enablePlugin("test-plugin")
        assertTrue(entry.enabled)

        verify(exactly = 3) { pluginManagementRepository.save(entry) }
    }

    @Test
    fun `should handle different plugins independently`() {
        val entry1 = PluginManagementEntry("plugin1", enabled = false, priority = 1)
        val entry2 = PluginManagementEntry("plugin2", enabled = true, priority = 2)

        every { pluginManagementRepository.findByIdOrNull("plugin1") } returns entry1
        every { pluginManagementRepository.findByIdOrNull("plugin2") } returns entry2
        every { pluginManagementRepository.save(any()) } returnsArgument 0

        statusProvider.enablePlugin("plugin1")
        statusProvider.disablePlugin("plugin2")

        assertTrue(entry1.enabled)
        assertFalse(entry2.enabled)
    }
}