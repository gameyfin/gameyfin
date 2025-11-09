package org.gameyfin.app.core.jobs

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JobRunResultTest {

    @Test
    fun `should create JobRunResult with all fields`() {
        val startedAt = LocalDateTime.now()
        val finishedAt = LocalDateTime.now().plusMinutes(5)

        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Job completed successfully"
        )

        assertEquals(1L, result.id)
        assertEquals("TestJob", result.jobName)
        assertEquals(startedAt, result.startedAt)
        assertEquals(finishedAt, result.finishedAt)
        assertEquals(JobRunStatus.SUCCESS, result.status)
        assertEquals("Job completed successfully", result.message)
    }

    @Test
    fun `should create JobRunResult with null id`() {
        val result = JobRunResult(
            id = null,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertNull(result.id)
    }

    @Test
    fun `should create JobRunResult with null finishedAt`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = null,
            status = JobRunStatus.IN_PROGRESS,
            message = null
        )

        assertNull(result.finishedAt)
        assertEquals(JobRunStatus.IN_PROGRESS, result.status)
    }

    @Test
    fun `should create JobRunResult with null message`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = null
        )

        assertNull(result.message)
    }

    @Test
    fun `should create JobRunResult with IN_PROGRESS status`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = null,
            status = JobRunStatus.IN_PROGRESS,
            message = "Job is running"
        )

        assertEquals(JobRunStatus.IN_PROGRESS, result.status)
        assertNull(result.finishedAt)
    }

    @Test
    fun `should create JobRunResult with SUCCESS status`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertEquals(JobRunStatus.SUCCESS, result.status)
        assertNotNull(result.finishedAt)
    }

    @Test
    fun `should create JobRunResult with FAILED status`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.FAILED,
            message = "Job failed due to error"
        )

        assertEquals(JobRunStatus.FAILED, result.status)
        assertEquals("Job failed due to error", result.message)
    }

    @Test
    fun `should create JobRunResult with CANCELLED status`() {
        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.CANCELLED,
            message = "Job was cancelled"
        )

        assertEquals(JobRunStatus.CANCELLED, result.status)
        assertEquals("Job was cancelled", result.message)
    }

    @Test
    fun `data class copy should work correctly`() {
        val original = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = null,
            status = JobRunStatus.IN_PROGRESS,
            message = "Running"
        )

        val finished = original.copy(
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = "Completed"
        )

        assertEquals(original.id, finished.id)
        assertEquals(original.jobName, finished.jobName)
        assertEquals(original.startedAt, finished.startedAt)
        assertNotNull(finished.finishedAt)
        assertEquals(JobRunStatus.SUCCESS, finished.status)
        assertEquals("Completed", finished.message)
    }

    @Test
    fun `data class equals should work correctly`() {
        val startedAt = LocalDateTime.now()
        val finishedAt = LocalDateTime.now()

        val result1 = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        val result2 = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertEquals(result1, result2)
    }

    @Test
    fun `data class hashCode should work correctly`() {
        val startedAt = LocalDateTime.now()
        val finishedAt = LocalDateTime.now()

        val result1 = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        val result2 = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertEquals(result1.hashCode(), result2.hashCode())
    }

    @Test
    fun `should handle long job names`() {
        val longJobName = "A".repeat(255)

        val result = JobRunResult(
            id = 1L,
            jobName = longJobName,
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertEquals(longJobName, result.jobName)
        assertEquals(255, result.jobName.length)
    }

    @Test
    fun `should handle long messages`() {
        val longMessage = "Error: " + "X".repeat(1000)

        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.FAILED,
            message = longMessage
        )

        assertEquals(longMessage, result.message)
    }

    @Test
    fun `should create JobRunResult with special characters in message`() {
        val messageWithSpecialChars = "Error: Database connection failed! @#$%^&*()_+-=[]{}|;':\",./<>?"

        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.FAILED,
            message = messageWithSpecialChars
        )

        assertEquals(messageWithSpecialChars, result.message)
    }

    @Test
    fun `should handle multi-line messages`() {
        val multiLineMessage = """
            Error occurred while processing job.
            Stack trace:
                at line 1
                at line 2
        """.trimIndent()

        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.FAILED,
            message = multiLineMessage
        )

        assertEquals(multiLineMessage, result.message)
    }

    @Test
    fun `finishedAt should be after or equal to startedAt for completed jobs`() {
        val startedAt = LocalDateTime.now()
        val finishedAt = startedAt.plusMinutes(10)

        val result = JobRunResult(
            id = 1L,
            jobName = "TestJob",
            startedAt = startedAt,
            finishedAt = finishedAt,
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assert(result.finishedAt!! >= result.startedAt)
    }

    @Test
    fun `should create JobRunResult without id for unsaved entity`() {
        val result = JobRunResult(
            jobName = "TestJob",
            startedAt = LocalDateTime.now(),
            finishedAt = LocalDateTime.now(),
            status = JobRunStatus.SUCCESS,
            message = "Success"
        )

        assertNull(result.id)
        assertNotNull(result.jobName)
        assertNotNull(result.startedAt)
    }
}

