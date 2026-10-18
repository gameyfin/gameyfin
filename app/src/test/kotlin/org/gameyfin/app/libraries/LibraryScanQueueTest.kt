package org.gameyfin.app.libraries

import io.mockk.*
import org.gameyfin.app.libraries.enums.ScanType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.*
import kotlin.test.*

class LibraryScanQueueTest {
    private class Timer(val action: Runnable) {
        var cancelled = false
        val future = mockk<ScheduledFuture<*>>()
        init {
            every { future.cancel(false) } answers { cancelled = true; true }
        }
    }

    private val executor = mockk<ExecutorService>(relaxed = true)
    private val scheduler = mockk<ScheduledExecutorService>(relaxed = true)
    private val workers = mutableListOf<FutureTask<*>>()
    private val timers = mutableListOf<Timer>()
    private val scans = mutableListOf<Pair<Long, ScanType>>()
    private var scanAction: (Long, ScanType) -> Unit = { id, type -> scans.add(id to type) }
    private lateinit var queue: LibraryScanQueue

    @BeforeEach
    fun setup() {
        every { executor.submit(any<Runnable>()) } answers {
            FutureTask(firstArg<Runnable>(), null).also { workers.add(it) }
        }
        every { scheduler.schedule(any<Runnable>(), 30_000L, TimeUnit.MILLISECONDS) } answers {
            Timer(firstArg()).also { timers.add(it) }.future
        }
        queue = LibraryScanQueue({ id, type -> scanAction(id, type) }, executor, scheduler)
    }

    @AfterEach
    fun tearDown() { queue.close() }

    private fun runScan() { workers.removeAt(0).run() }
    private fun fireTimer() { timers.last { !it.cancelled }.action.run() }

    @Test
    fun `watcher burst resets quiet period and runs only one scan`() {
        repeat(200) { queue.request(1L, ScanType.QUICK, debounce = true) }
        assertTrue(workers.isEmpty())
        assertEquals(199, timers.count { it.cancelled })
        // Model cancelled callbacks that were already dispatched before cancellation.
        timers.dropLast(1).forEach { it.action.run() }
        assertTrue(workers.isEmpty())
        fireTimer()
        runScan()
        assertEquals(listOf(1L to ScanType.QUICK), scans)
        assertTrue(workers.isEmpty())
    }

    @Test
    fun `requests during active scan coalesce into one delayed follow-up`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        repeat(200) { queue.request(1L, ScanType.QUICK, debounce = true) }
        assertEquals(1, workers.size)
        assertTrue(timers.isEmpty())
        runScan()
        assertEquals(1, timers.size)
        fireTimer()
        runScan()
        assertEquals(2, scans.size)
        assertEquals(1, timers.size)
    }

    @Test
    fun `queued full request retains type after quick watcher events`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(1L, ScanType.FULL, debounce = false)
        queue.request(1L, ScanType.QUICK, debounce = true)
        runScan()
        fireTimer()
        runScan()
        assertEquals(listOf(1L to ScanType.QUICK, 1L to ScanType.FULL), scans)
    }

    @Test
    fun `scheduled request retains type after quick watcher event`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(1L, ScanType.SCHEDULED, debounce = false)
        queue.request(1L, ScanType.QUICK, debounce = true)
        runScan()
        fireTimer()
        runScan()
        assertEquals(ScanType.SCHEDULED, scans.last().second)
    }

    @Test
    fun `manual request consumes pending watcher scan and invalidates timer`() {
        queue.request(1L, ScanType.QUICK, debounce = true)
        val oldTimer = timers.single()
        queue.request(1L, ScanType.FULL, debounce = false)
        oldTimer.action.run()
        assertTrue(oldTimer.cancelled)
        assertEquals(1, workers.size)
        runScan()
        assertEquals(listOf(1L to ScanType.FULL), scans)
        oldTimer.action.run()
        assertTrue(workers.isEmpty())
    }

    @Test
    fun `event after completion resets follow-up cooldown`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(1L, ScanType.QUICK, debounce = true)
        runScan()
        val completionTimer = timers.single()
        queue.request(1L, ScanType.QUICK, debounce = true)
        completionTimer.action.run()
        assertTrue(workers.isEmpty())
        fireTimer()
        runScan()
        assertEquals(2, scans.size)
    }

    @Test
    fun `events during follow-up schedule another scan without recursion`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(1L, ScanType.QUICK, debounce = true)
        runScan()
        fireTimer()
        queue.request(1L, ScanType.QUICK, debounce = true)
        runScan()
        assertEquals(2, scans.size)
        fireTimer()
        runScan()
        assertEquals(3, scans.size)
    }

    @Test
    fun `failed scan releases active state and preserves queued request`() {
        scanAction = { _, _ -> throw IllegalStateException("Filesystem failure") }
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(1L, ScanType.FULL, debounce = false)
        runScan()
        scanAction = { id, type -> scans.add(id to type) }
        fireTimer()
        runScan()
        queue.request(1L, ScanType.QUICK, debounce = false)
        runScan()
        assertEquals(listOf(1L to ScanType.FULL, 1L to ScanType.QUICK), scans)
    }

    @Test
    fun `different libraries can run independently`() {
        queue.request(1L, ScanType.QUICK, debounce = false)
        queue.request(2L, ScanType.FULL, debounce = false)
        assertEquals(2, workers.size)
        runScan()
        runScan()
        assertEquals(listOf(1L to ScanType.QUICK, 2L to ScanType.FULL), scans)
    }

    @Test
    fun `concurrent watcher requests produce only one runnable scan`() {
        Executors.newFixedThreadPool(4).use { callers ->
            (1..100).map { callers.submit { queue.request(1L, ScanType.QUICK, debounce = true) } }
                .forEach { it.get(5, TimeUnit.SECONDS) }
        }
        timers.forEach { it.action.run() }
        assertEquals(1, workers.size)
        runScan()
        assertEquals(1, scans.size)
    }

    @Test
    fun `executor rejection does not leave library stuck active`() {
        every { executor.submit(any<Runnable>()) } throws RejectedExecutionException()
        assertThrows<RejectedExecutionException> { queue.request(1L, ScanType.QUICK, debounce = false) }
        every { executor.submit(any<Runnable>()) } answers {
            FutureTask(firstArg<Runnable>(), null).also { workers.add(it) }
        }
        queue.request(1L, ScanType.QUICK, debounce = false)
        runScan()
        assertEquals(1, scans.size)
    }

    @Test
    fun `shutdown cancels pending timers and ignores late requests`() {
        queue.request(1L, ScanType.QUICK, debounce = true)
        queue.close()
        timers.single().action.run()
        queue.request(1L, ScanType.FULL, debounce = false)
        assertTrue(timers.single().cancelled)
        assertTrue(workers.isEmpty())
        verify { scheduler.shutdownNow(); executor.shutdownNow() }
    }
}
