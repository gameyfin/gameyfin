package org.gameyfin.app.core.jobs

import io.mockk.*
import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LibraryScanJobTest {

    private lateinit var libraryScanService: LibraryScanService
    private lateinit var job: LibraryScanJob

    @BeforeEach
    fun setup() {
        libraryScanService = mockk<LibraryScanService>(relaxed = true)
        job = LibraryScanJob(libraryScanService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `name should return LibraryScan`() {
        assertEquals("LibraryScan", job.name)
    }

    @Test
    fun `run should trigger scan with SCHEDULED type and null library`() {
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        job.run()

        verify(exactly = 1) { libraryScanService.triggerScan(ScanType.SCHEDULED, null) }
    }

    @Test
    fun `run should return SUCCESS result when scan completes successfully`() {
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        val result = job.run()

        assertEquals("LibraryScan", result.jobName)
        assertEquals(JobRunStatus.SUCCESS, result.status)
        assertEquals("Library scan completed successfully", result.message)
        assertNotNull(result.startedAt)
        assertNotNull(result.finishedAt)
        assertNull(result.id)
    }

    @Test
    fun `run should return FAILED result when scan throws exception`() {
        val errorMessage = "Database connection failed"
        every { libraryScanService.triggerScan(any(), any()) } throws RuntimeException(errorMessage)

        val result = job.run()

        assertEquals("LibraryScan", result.jobName)
        assertEquals(JobRunStatus.FAILED, result.status)
        assertEquals(errorMessage, result.message)
        assertNotNull(result.startedAt)
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `run should return FAILED result with null message when exception has no message`() {
        every { libraryScanService.triggerScan(any(), any()) } throws RuntimeException()

        val result = job.run()

        assertEquals("LibraryScan", result.jobName)
        assertEquals(JobRunStatus.FAILED, result.status)
        assertNull(result.message)
        assertNotNull(result.startedAt)
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `run should set finishedAt even when exception is thrown`() {
        every { libraryScanService.triggerScan(any(), any()) } throws RuntimeException("Error")

        val result = job.run()

        assertNotNull(result.startedAt)
        assertNotNull(result.finishedAt)
        assert(result.finishedAt >= result.startedAt)
    }

    @Test
    fun `run should handle IllegalArgumentException`() {
        val errorMessage = "Invalid scan type"
        every { libraryScanService.triggerScan(any(), any()) } throws IllegalArgumentException(errorMessage)

        val result = job.run()

        assertEquals(JobRunStatus.FAILED, result.status)
        assertEquals(errorMessage, result.message)
    }

    @Test
    fun `run should handle generic Exception`() {
        val errorMessage = "Unexpected error"
        every { libraryScanService.triggerScan(any(), any()) } throws Exception(errorMessage)

        val result = job.run()

        assertEquals(JobRunStatus.FAILED, result.status)
        assertEquals(errorMessage, result.message)
    }

    @Test
    fun `run should have finishedAt after startedAt on success`() {
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        val result = job.run()

        assertNotNull(result.startedAt)
        assertNotNull(result.finishedAt)
        assert(result.finishedAt >= result.startedAt)
    }

    @Test
    fun `multiple runs should each create new results with different timestamps`() {
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        val result1 = job.run()
        Thread.sleep(10) // Ensure different timestamps
        val result2 = job.run()

        assert(result2.startedAt > result1.startedAt)
        verify(exactly = 2) { libraryScanService.triggerScan(ScanType.SCHEDULED, null) }
    }
}

