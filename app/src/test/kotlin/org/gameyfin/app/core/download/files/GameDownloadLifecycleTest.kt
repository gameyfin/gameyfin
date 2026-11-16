package org.gameyfin.app.core.download.files

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameDownloadLifecycleTest {
    @Test
    fun `removal closes all prepared downloads for only the selected game`() {
        var closed = 0
        fun response(id: Long) = GameDownloadLifecycle.prepare(id, MockHttpServletRequest(), { mockk() }) { _, transfer ->
            transfer.own(Closeable { closed++ })
            ResponseEntity.ok().body(StreamingResponseBody { it.write(1) })
        }
        val first = response(1001)
        val second = response(1001)
        val other = response(1002)
        try {
            GameDownloadLifecycle.remove(1001) {
                assertEquals(2, closed, "Sources must be closed before deleting files")
                val error = assertFailsWith<IOException> { first.body!!.writeTo(ByteArrayOutputStream()) }
                assertTrue(error.message!!.contains("game is being removed"))
                assertFailsWith<IOException> { second.body!!.writeTo(ByteArrayOutputStream()) }
                assertFailsWith<IOException> { response(1001) }
            }
            val output = ByteArrayOutputStream()
            other.body!!.writeTo(output)
            assertEquals(listOf<Byte>(1), output.toByteArray().toList())
        } finally {
            listOf(first, second, other).forEach { (it.body as Closeable).close() }
        }
    }

    @Test
    fun `removal interrupts streaming and does not wait for worker completion`() {
        val started = CountDownLatch(1)
        val release = CompletableFuture<Unit>()
        val closed = AtomicBoolean()
        val interrupted = AtomicBoolean()
        val executor = Executors.newSingleThreadExecutor()
        val output = ByteArrayOutputStream()
        val response = GameDownloadLifecycle.prepare(1003, MockHttpServletRequest(), { mockk() }) { _, transfer ->
            transfer.own(Closeable { closed.set(true) })
            ResponseEntity.ok().body(StreamingResponseBody {
                started.countDown()
                release.join() // Deliberately uninterruptible: deletion must not join this worker.
                interrupted.set(Thread.currentThread().isInterrupted)
                it.write(1)
            })
        }
        val task = executor.submit<Throwable?> {
            runCatching { response.body!!.writeTo(output) }.exceptionOrNull()
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            GameDownloadLifecycle.remove(1003) {
                assertTrue(closed.get())
                assertFalse(task.isDone, "Removal must proceed without waiting for the worker")
            }
            release.complete(Unit)
            assertTrue(task.get(5, TimeUnit.SECONDS) is IOException)
            assertTrue(interrupted.get())
            assertEquals(0, output.size(), "A cancelled transfer must not write buffered data")
        } finally {
            release.complete(Unit)
            (response.body as Closeable).close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `resources produced after cancellation are immediately closed`() {
        val started = CountDownLatch(1)
        val release = CompletableFuture<Unit>()
        val closed = AtomicBoolean()
        val executor = Executors.newSingleThreadExecutor()
        val task = executor.submit<Throwable?> {
            runCatching {
                GameDownloadLifecycle.prepare(1004, MockHttpServletRequest(), { mockk() }) { _, transfer ->
                    started.countDown()
                    release.join()
                    transfer.own(Closeable { closed.set(true) })
                    ResponseEntity.ok().body(StreamingResponseBody { it.write(1) })
                }
            }.exceptionOrNull()
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            GameDownloadLifecycle.remove(1004) {}
            release.complete(Unit)
            assertTrue(task.get(5, TimeUnit.SECONDS) is IOException)
            assertTrue(closed.get())
        } finally {
            release.complete(Unit)
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancellation failure cancels remaining sources and prevents deletion`() {
        val closed = AtomicBoolean()
        val first = GameDownloadLifecycle.prepare(1005, MockHttpServletRequest(), { mockk() }) { _, transfer ->
            transfer.own(Closeable { throw IOException("Cannot close") })
            ResponseEntity.ok().body(StreamingResponseBody {})
        }
        val second = GameDownloadLifecycle.prepare(1005, MockHttpServletRequest(), { mockk() }) { _, transfer ->
            transfer.own(Closeable { closed.set(true) })
            ResponseEntity.ok().body(StreamingResponseBody {})
        }
        var deleted = false
        try {
            assertFailsWith<IOException> { GameDownloadLifecycle.remove(1005) { deleted = true } }
            assertTrue(closed.get())
            assertFalse(deleted)
        } finally {
            (first.body as Closeable).close()
            (second.body as Closeable).close()
        }
    }
}
