package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.entities.Library
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.ApplicationEventPublisher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DeletionTaskServiceTest {
    private lateinit var runs: TaskRunService
    private lateinit var service: DeletionTaskService
    private lateinit var libraries: LibraryService
    private lateinit var collections: CollectionService
    private val entries = ConcurrentHashMap<String, TaskRunEntry>()

    @BeforeEach
    fun setup() {
        val registry = TaskRegistry(mockk<ApplicationEventPublisher>(relaxed = true))
        runs = TaskRunService(registry, inMemoryTaskRunRepository(entries), mockk(relaxed = true))
        libraries = mockk()
        collections = mockk()
        every { libraries.getById(1) } returns Library(name = "Test")
        every { collections.getById(1) } returns Collection(name = "Test")
        service = DeletionTaskService(registry, runs, libraries, collections)
        service.registerTasks()
    }

    @AfterEach
    fun teardown() = runs.shutdown()

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `deletion returns while work is running and exposes indeterminate progress`(library: Boolean) {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val body = {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        every { libraries.delete(1) } answers { body() }
        every { collections.delete(1) } answers { body() }

        try {
            val run = if (library) service.deleteLibrary(1) else service.deleteCollection(1)
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val active = assertNotNull(runs.getActiveRun(run.id))
            assertEquals(TaskRunStatus.IN_PROGRESS, active.status)
            assertFalse(active.cancellable)
            assertFalse(active.visibleInOverview)
            assertNotNull(active.step)
            assertNull(active.step.total)
            release.countDown()
            awaitFinished(run.id)
            assertEquals(TaskRunStatus.COMPLETED, entries.getValue(run.id).status)
            assertFalse(runs.toDto(entries.getValue(run.id)).visibleInOverview)
        } finally {
            release.countDown()
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `deletion failures are reported in the task run`(library: Boolean) {
        every { libraries.delete(1) } throws IllegalStateException("Deletion failed")
        every { collections.delete(1) } throws IllegalStateException("Deletion failed")

        val run = if (library) service.deleteLibrary(1) else service.deleteCollection(1)
        awaitFinished(run.id)

        assertEquals(TaskRunStatus.FAILED, entries.getValue(run.id).status)
        assertEquals("Deletion failed", entries.getValue(run.id).errorMessage)
    }

    private fun awaitFinished(id: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (runs.getActiveRun(id) != null) {
            check(System.nanoTime() < deadline) { "Deletion did not finish" }
            Thread.sleep(10)
        }
    }
}
