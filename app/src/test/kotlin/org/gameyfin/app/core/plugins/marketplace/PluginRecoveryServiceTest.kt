package org.gameyfin.app.core.plugins.marketplace

import io.mockk.*
import org.gameyfin.app.core.notifications.NotificationStore
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.management.PluginsDiscoveredEvent
import org.gameyfin.app.core.tasks.*
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.Test
import kotlin.test.*

class PluginRecoveryServiceTest {
    private val candidates = mockk<PluginRecoveryRepository>()
    private val installer = mockk<GameyfinUpdateManager>(relaxed = true)
    private val registry = TaskRegistry(mockk(relaxed = true))
    private val runs = mockk<TaskRunService>(relaxed = true)
    private val notifications = mockk<NotificationStore>(relaxed = true)
    private val service = PluginRecoveryService(
        candidates, installer, mockk<PluginService>(relaxed = true), registry, runs, notifications
    ).also { it.register() }

    @Test
    fun `migration cannot be scheduled or run manually like image recovery`() {
        val definition = registry.require(PluginRecoveryService.TASK)
        assertFalse(definition.schedulable)
        assertFalse(definition.manuallyTriggerable)
    }

    @Test
    fun `fresh installations and completed migrations do not trigger recovery`() {
        every { candidates.findAllByCompletedFalse() } returns emptyList()
        service.afterDiscovery(PluginsDiscoveredEvent())
        verify(exactly = 0) { runs.trigger(any(), any(), any()) }
    }

    @Test
    fun `pending migration starts only after discovery and resumes on next startup`() {
        every { candidates.findAllByCompletedFalse() } returns listOf(PluginRecoveryCandidate("legacy"))
        verify(exactly = 0) { runs.trigger(any(), any(), any()) }
        service.afterDiscovery(PluginsDiscoveredEvent())
        service.afterDiscovery(PluginsDiscoveredEvent())
        verify(exactly = 2) { runs.trigger(PluginRecoveryService.TASK, TaskTrigger.SYSTEM, any()) }

        every { candidates.findAllByCompletedFalse() } returns emptyList()
        service.afterDiscovery(PluginsDiscoveredEvent())
        verify(exactly = 2) { runs.trigger(any(), any(), any()) }
    }
}
