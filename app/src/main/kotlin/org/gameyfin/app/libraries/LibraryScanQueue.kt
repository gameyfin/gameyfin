package org.gameyfin.app.libraries

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.libraries.enums.ScanType
import java.time.Duration
import java.util.concurrent.*

/** Coalesces requests per library without overlapping scans or losing requests during completion. */
internal class LibraryScanQueue(
    private val scan: (Long, ScanType) -> Unit,
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
    private val scheduler: ScheduledExecutorService = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    },
    private val quietPeriod: Duration = Duration.ofSeconds(30)
) : AutoCloseable {
    private class State {
        var running = false
        var pending: ScanType? = null
        var timer: ScheduledFuture<*>? = null
        var generation = 0L
    }

    private val log = KotlinLogging.logger {}
    private val states = mutableMapOf<Long, State>()
    private var closed = false

    fun request(libraryId: Long, scanType: ScanType, debounce: Boolean) {
        synchronized(states) {
            if (closed) return
            val state = states.getOrPut(libraryId) { State() }
            // A watcher QUICK scan must never replace an explicitly requested FULL scan.
            state.pending = when {
                scanType == ScanType.FULL || state.pending == ScanType.FULL -> ScanType.FULL
                scanType == ScanType.SCHEDULED || state.pending == ScanType.SCHEDULED -> ScanType.SCHEDULED
                else -> ScanType.QUICK
            }
            if (!state.running) {
                if (debounce) schedule(libraryId, state) else launch(libraryId, state)
            }
        }
    }

    // All state transitions, including cancellation and completion, hold the same lock.
    private fun schedule(libraryId: Long, state: State) {
        state.timer?.cancel(false)
        val generation = ++state.generation
        state.timer = scheduler.schedule({
            synchronized(states) {
                // A cancelled callback may already be running and waiting for this lock.
                if (!closed && states[libraryId] === state && state.generation == generation && !state.running) {
                    launch(libraryId, state)
                }
            }
        }, quietPeriod.toMillis(), TimeUnit.MILLISECONDS)
    }

    private fun launch(libraryId: Long, state: State) {
        val scanType = state.pending ?: return
        state.pending = null
        state.timer?.cancel(false)
        state.timer = null
        state.generation++
        state.running = true
        try {
            executor.submit {
                try {
                    scan(libraryId, scanType)
                } catch (e: Exception) {
                    log.error(e) { "Failed to run $scanType scan for library $libraryId" }
                } finally {
                    synchronized(states) {
                        state.running = false
                        if (!closed && state.pending != null) {
                            schedule(libraryId, state)
                        } else {
                            states.remove(libraryId)
                        }
                    }
                }
            }
        } catch (e: RejectedExecutionException) {
            states.remove(libraryId)
            throw e
        }
    }

    override fun close() {
        synchronized(states) {
            closed = true
            states.values.forEach { it.timer?.cancel(false) }
            states.clear()
        }
        scheduler.shutdownNow()
        executor.shutdownNow()
    }
}
