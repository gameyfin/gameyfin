package org.gameyfin.app.core.plugins.marketplace

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.tasks.*
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.pf4j.PluginState
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PluginInstallationTaskServiceTest {
    enum class Operation { INSTALL, UPDATE, UNINSTALL }

    private lateinit var runs: TaskRunService
    private lateinit var service: PluginInstallationTaskService
    private lateinit var marketplace: PluginMarketplaceService
    private lateinit var registry: TaskRegistry
    private val entries = ConcurrentHashMap<String, TaskRunEntry>()

    @BeforeEach
    fun setup() {
        registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        runs = TaskRunService(registry, inMemoryTaskRunRepository(entries), mockk(relaxed = true))
        marketplace = mockk()
        service = PluginInstallationTaskService(registry, runs, marketplace)
        service.registerTasks()
    }

    @AfterEach
    fun teardown() = runs.shutdown()

    @ParameterizedTest
    @EnumSource(Operation::class)
    fun `operations return before completion and expose hidden indeterminate tasks`(operation: Operation) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val work = {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            null
        }
        every { marketplace.install("market", "plugin", "1.0") } answers { work() }
        every { marketplace.update("plugin", "1.0") } answers { work() }
        every { marketplace.uninstall("plugin") } answers { work() }

        try {
            val run = start(operation)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val active = assertNotNull(runs.getActiveRun(run.id))
            assertEquals(TaskRunStatus.IN_PROGRESS, active.status)
            assertFalse(active.visibleInOverview)
            assertFalse(active.cancellable)
            assertNull(assertNotNull(active.step).total)
            release.countDown()
            awaitFinished(run.id)
            assertEquals(TaskRunStatus.COMPLETED, entries.getValue(run.id).status)
            assertFalse(runs.toDto(entries.getValue(run.id)).visibleInOverview)
        } finally {
            release.countDown()
        }
        registry.getAll().forEach {
            assertFalse(it.schedulable)
            assertFalse(it.manuallyTriggerable)
        }
    }

    @ParameterizedTest
    @EnumSource(Operation::class)
    fun `operation errors are exposed as failed task runs`(operation: Operation) {
        every { marketplace.install(any(), any(), any()) } throws IllegalStateException("Operation failed")
        every { marketplace.update(any(), any()) } throws IllegalStateException("Operation failed")
        every { marketplace.uninstall(any()) } throws IllegalStateException("Operation failed")

        val run = start(operation)
        awaitFinished(run.id)

        assertEquals(TaskRunStatus.FAILED, entries.getValue(run.id).status)
        assertEquals("Operation failed", entries.getValue(run.id).errorMessage)
    }

    @Test
    fun `installation summary explains disabled plugins`() {
        val plugin = plugin(PluginState.DISABLED)
        every { marketplace.install(any(), any(), any()) } returns plugin

        val run = start(Operation.INSTALL)
        awaitFinished(run.id)

        assertContains(assertNotNull(entries.getValue(run.id).summary), "disabled")
    }

    @Test
    fun `uninstallation summary includes fallback version and state`() {
        every { marketplace.uninstall(any()) } returns plugin(PluginState.STARTED)

        val run = start(Operation.UNINSTALL)
        awaitFinished(run.id)

        val summary = assertNotNull(entries.getValue(run.id).summary)
        assertContains(summary, "reverted to local version 1.0")
        assertContains(summary, "active")
    }

    private fun plugin(state: PluginState) = mockk<PluginDto> {
        every { name } returns "Plugin"
        every { version } returns "1.0"
        every { this@mockk.state } returns state
    }

    private fun start(operation: Operation): TaskRunDto = when (operation) {
        Operation.INSTALL -> service.install("market", "plugin", "1.0")
        Operation.UPDATE -> service.update("plugin", "1.0")
        Operation.UNINSTALL -> service.uninstall("plugin")
    }

    private fun awaitFinished(id: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (runs.getActiveRun(id) != null) {
            check(System.nanoTime() < deadline) { "Plugin operation did not finish" }
            Thread.sleep(10)
        }
    }
}
