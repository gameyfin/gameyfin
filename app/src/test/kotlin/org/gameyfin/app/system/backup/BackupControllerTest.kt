package org.gameyfin.app.system.backup

import io.mockk.*
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.setup.SetupService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.div
import kotlin.io.path.writeText

class BackupControllerTest {

    @TempDir
    lateinit var dir: Path

    private val backupService = mockk<BackupService>(relaxed = true)
    private val setupService = mockk<SetupService>()
    private lateinit var controller: BackupController

    @BeforeEach
    fun setUp() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { isCurrentUserAdmin() } returns true
        controller = BackupController(backupService, setupService)
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun dto(restorable: Boolean = true) =
        BackupDto("b.zip", 1, Instant.now(), null, null, false, restorable, if (restorable) null else "bad")

    @Test
    fun `downloadBackup serves the resolved file`() {
        val file = dir / "b.zip"
        file.writeText("data")
        every { backupService.getBackupFile("b.zip") } returns file

        val response = controller.downloadBackup("b.zip")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(4, response.headers.contentLength)
        assertTrue(response.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.contains("b.zip"))
    }

    @Test
    fun `downloadBackup is forbidden for non admins`() {
        every { isCurrentUserAdmin() } returns false

        val e = assertThrows(ResponseStatusException::class.java) { controller.downloadBackup("b.zip") }
        assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
        verify(exactly = 0) { backupService.getBackupFile(any()) }
    }

    @Test
    fun `uploadBackup stores the request body`() {
        val request = mockk<jakarta.servlet.http.HttpServletRequest>()
        val stream = mockk<jakarta.servlet.ServletInputStream>()
        every { request.inputStream } returns stream
        every { backupService.storeUploadedBackup(any()) } returns dto()

        assertEquals("b.zip", controller.uploadBackup(request).name)
    }

    @Test
    fun `uploadBackup is forbidden for non admins`() {
        every { isCurrentUserAdmin() } returns false
        assertThrows(ResponseStatusException::class.java) { controller.uploadBackup(mockk()) }
    }

    private fun requestWith(bytes: ByteArray): jakarta.servlet.http.HttpServletRequest {
        val request = mockk<jakarta.servlet.http.HttpServletRequest>()
        every { request.inputStream } returns object : jakarta.servlet.ServletInputStream() {
            val s = ByteArrayInputStream(bytes)
            override fun read() = s.read()
            override fun isFinished() = s.available() == 0
            override fun isReady() = true
            override fun setReadListener(l: jakarta.servlet.ReadListener?) {}
        }
        return request
    }

    @Test
    fun `setup restore is forbidden after setup completed`() {
        every { setupService.isSetupCompleted() } returns true

        val e = assertThrows(ResponseStatusException::class.java) { controller.restoreDuringSetup(requestWith(ByteArray(0))) }
        assertEquals(HttpStatus.FORBIDDEN, e.statusCode)
    }

    @Test
    fun `setup restore restores a valid backup`() {
        every { setupService.isSetupCompleted() } returns false
        every { backupService.storeUploadedBackup(any()) } returns dto()

        val response = controller.restoreDuringSetup(requestWith(ByteArray(1)))

        assertEquals(HttpStatus.ACCEPTED, response.statusCode)
        verify { backupService.restoreBackup("b.zip", createSafetyBackup = false) }
    }

    @Test
    fun `setup restore deletes and rejects a non restorable backup`() {
        every { setupService.isSetupCompleted() } returns false
        every { backupService.storeUploadedBackup(any()) } returns dto(restorable = false)

        assertThrows(InvalidBackupException::class.java) { controller.restoreDuringSetup(requestWith(ByteArray(1))) }
        verify { backupService.deleteBackup("b.zip") }
        verify(exactly = 0) { backupService.restoreBackup(any(), any()) }
    }

    @Test
    fun `exception handlers map to status codes`() {
        assertEquals(HttpStatus.BAD_REQUEST, controller.handleBadRequest(IllegalArgumentException("x")).statusCode)
        assertEquals(HttpStatus.CONFLICT, controller.handleConflict(IllegalStateException("x")).statusCode)
        val r = controller.handleResponseStatus(ResponseStatusException(HttpStatus.FORBIDDEN, "no"))
        assertEquals(HttpStatus.FORBIDDEN, r.statusCode)
        assertEquals("no", r.body!!["message"])
    }
}
