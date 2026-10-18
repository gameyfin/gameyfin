package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.tasks.logging.TaskRunLogs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskRunLogControllerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var controller: TaskRunLogController

    @BeforeEach
    fun setup() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { isCurrentUserAdmin() } returns true
        val config = mockk<ConfigService>()
        every { config.get(ConfigProperties.Logs.Folder) } returns tempDir.toString()
        controller = TaskRunLogController(TaskRunLogs(config))
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `downloadLog should return the complete log file`() {
        val runId = UUID.randomUUID().toString()
        val file = tempDir.resolve(TaskRunLogs.TASK_LOG_DIRECTORY).createDirectories().resolve("$runId.log")
        file.writeText("line 1\nline 2\n")

        val response = controller.downloadLog(runId)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("line 1\nline 2\n", response.body!!.inputStream.use { String(it.readAllBytes()) })
        assertTrue(response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.contains("task-run-$runId.log"))
    }

    @Test
    fun `downloadLog should fail for missing logs`() {
        val e = assertThrows<ResponseStatusException> { controller.downloadLog(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.NOT_FOUND, e.statusCode)
    }

    @Test
    fun `downloadLog should reject invalid run IDs`() {
        val e = assertThrows<ResponseStatusException> { controller.downloadLog("../../secret") }
        assertEquals(HttpStatus.BAD_REQUEST, e.statusCode)
    }

    @Test
    fun `downloadLog should only be available for admins`() {
        every { isCurrentUserAdmin() } returns false

        val e = assertThrows<ResponseStatusException> { controller.downloadLog(UUID.randomUUID().toString()) }
        assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
    }
}
