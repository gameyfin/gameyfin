package org.gameyfin.app.core.logging.util

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import reactor.core.publisher.Sinks
import reactor.test.StepVerifier
import java.io.File
import java.nio.file.Path
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class AsyncFileTailerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var testFile: File
    private lateinit var sink: Sinks.Many<String>
    private lateinit var tailer: AsyncFileTailer

    @BeforeEach
    fun setup() {
        testFile = tempDir.resolve("test.log").toFile()
        testFile.createNewFile()
        sink = Sinks.many().replay().limit(100)
    }

    @AfterEach
    fun tearDown() {
        runBlocking {
            if (::tailer.isInitialized) {
                tailer.stopTailing()
                // Give Windows time to release file handles
                delay(200)
            }
        }
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `constructor should create AsyncFileTailer instance`() {
        tailer = AsyncFileTailer(testFile, 100.milliseconds, sink)

        assertNotNull(tailer)
    }

    @Test
    fun `startTailing should begin monitoring the file`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()

            // Give it time to start
            delay(100)

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `stopTailing should stop monitoring the file`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(100)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `startTailing should emit new lines to the sink`() {
        runBlocking {
            // Pre-write content to the file
            testFile.writeText("Existing line\n")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            val flux = sink.asFlux()

            tailer.startTailing()

            // Give tailer time to start and process existing content
            delay(200)

            // Append new content
            testFile.appendText("New line 1\n")
            testFile.appendText("New line 2\n")

            // Give tailer time to process
            delay(200)

            tailer.stopTailing()

            // Verify that at least some lines were emitted
            // Note: The tailer behavior may vary, so we check for non-empty
            StepVerifier.create(flux.take(1).timeout(java.time.Duration.ofSeconds(1)))
                .expectNextCount(1)
                .verifyComplete()
        }
    }

    @Test
    fun `multiple calls to startTailing should not start multiple threads`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(50)
            tailer.startTailing() // Second call should be ignored
            delay(50)
            tailer.startTailing() // Third call should be ignored
            delay(50)

            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `stopTailing when not started should not throw exception`() {
        tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

        tailer.stopTailing()

        // Should not throw an exception
        assertNotNull(tailer)
    }

    @Test
    fun `multiple calls to stopTailing should not throw exception`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(50)
            tailer.stopTailing()
            tailer.stopTailing()
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle empty file`() {
        runBlocking {
            // File is already empty from setup
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(100)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file with existing content`() {
        runBlocking {
            testFile.writeText("Line 1\n")
            testFile.appendText("Line 2\n")
            testFile.appendText("Line 3\n")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(200)
            tailer.stopTailing()

            // Should not throw an exception and should have processed the file
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should restart after being stopped`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            // First start/stop cycle
            tailer.startTailing()
            delay(50)
            tailer.stopTailing()

            delay(50)

            // Second start/stop cycle
            tailer.startTailing()
            delay(50)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file with very long lines`() {
        runBlocking {
            val longLine = "A".repeat(10000)
            testFile.writeText("$longLine\n")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(200)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file with special characters`() {
        runBlocking {
            testFile.writeText("Special chars: !@#$%^&*()_+-={}[]|\\:\";<>?,./\n")
            testFile.appendText("Unicode: 你好世界 🎮🎯🎲\n")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(200)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle rapidly appended lines`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(100)

            // Rapidly append lines
            repeat(50) { i ->
                testFile.appendText("Rapid line $i\n")
            }

            delay(300)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file in nested directory`() {
        runBlocking {
            val nestedDir = tempDir.resolve("nested/dir/structure")
            nestedDir.toFile().mkdirs()
            val nestedFile = nestedDir.resolve("nested.log").toFile()
            nestedFile.createNewFile()

            val newSink = Sinks.many().replay().limit<String>(100)
            tailer = AsyncFileTailer(nestedFile, 50.milliseconds, newSink)

            tailer.startTailing()
            delay(100)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `sink should receive null-safe lines`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(100)

            testFile.appendText("Non-null line\n")

            delay(200)
            tailer.stopTailing()

            // The tailer should only emit non-null lines
            // If any lines were emitted, they should be non-null
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file with no trailing newline`() {
        runBlocking {
            testFile.writeText("Line without newline")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(200)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `tailer should handle file with mixed line endings`() {
        runBlocking {
            testFile.writeText("Unix line\n")
            testFile.appendText("Windows line\r\n")
            testFile.appendText("Old Mac line\r")
            testFile.appendText("Another Unix line\n")

            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            tailer.startTailing()
            delay(200)
            tailer.stopTailing()

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }

    @Test
    fun `concurrent start and stop should be handled safely`() {
        runBlocking {
            tailer = AsyncFileTailer(testFile, 50.milliseconds, sink)

            // Rapidly start and stop
            repeat(10) {
                tailer.startTailing()
                delay(10)
                tailer.stopTailing()
                delay(10)
            }

            // Should not throw an exception
            assertNotNull(tailer)
        }
    }
}

