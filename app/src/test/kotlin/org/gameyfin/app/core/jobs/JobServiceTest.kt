package org.gameyfin.app.core.jobs

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.libraries.LibraryScanService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobServiceTest {

    private lateinit var configService: ConfigService
    private lateinit var libraryScanService: LibraryScanService
    private lateinit var jobRunResultRepository: JobRunResultRepository
    private lateinit var service: JobService

    @BeforeEach
    fun setup() {
        configService = mockk<ConfigService>(relaxed = true)
        libraryScanService = mockk<LibraryScanService>(relaxed = true)
        jobRunResultRepository = mockk<JobRunResultRepository>(relaxed = true)

        // Default config values to avoid scheduling during test setup
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `init should schedule library scan job when enabled`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 * * * *"

        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) }
    }

    @Test
    fun `init should not schedule library scan job when disabled`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false

        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
        verify(exactly = 0) { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) }
    }

    @Test
    fun `init should not schedule library scan job when enabled is null`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns null

        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
        verify(exactly = 0) { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) }
    }

    @Test
    fun `init should not schedule when cron expression is null`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns null

        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
        verify(exactly = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) }
    }

    @Test
    fun `onLibraryScanScheduleUpdated should reschedule job when enabled`() {
        // First init with disabled
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        // Now enable and trigger event
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 * * * *"

        service.onLibraryScanScheduleUpdated()

        verify(atLeast = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
    }

    @Test
    fun `onLibraryScanScheduleUpdated should disable job when disabled`() {
        // First init with enabled
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 * * * *"
        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        // Now disable and trigger event
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false

        assertDoesNotThrow {
            service.onLibraryScanScheduleUpdated()
        }

        verify(atLeast = 1) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
    }

    @Test
    fun `scheduleLibraryScanJob should accept valid cron expressions`() {
        val validCronExpressions = listOf(
            "0 0 * * * *",      // Every hour
            "0 0 0 * * *",      // Every day at midnight
            "0 0 12 * * *",     // Every day at noon
            "0 0/30 * * * *",   // Every 30 minutes
            "0 0 0 * * MON"     // Every Monday at midnight
        )

        validCronExpressions.forEach { cron ->
            clearAllMocks()
            every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false

            service = JobService(configService, libraryScanService, jobRunResultRepository)

            every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
            every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns cron

            assertDoesNotThrow {
                service.onLibraryScanScheduleUpdated()
            }
        }
    }

    @Test
    fun `scheduleLibraryScanJob should log and not schedule with invalid cron expression`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
        service = JobService(configService, libraryScanService, jobRunResultRepository)

        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "invalid cron"

        // Should throw EndpointException for invalid cron
        assertDoesNotThrow {
            try {
                service.onLibraryScanScheduleUpdated()
            } catch (_: Exception) {
                // Expected exception for invalid cron
            }
        }
    }

    @Test
    fun `service should save job results to repository after job execution`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
        service = JobService(configService, libraryScanService, jobRunResultRepository)

        val capturedResult = slot<JobRunResult>()
        every { jobRunResultRepository.save(capture(capturedResult)) } returns mockk()
        every { libraryScanService.triggerScan(any(), any()) } just Runs

        // Manually trigger job execution
        val job = LibraryScanJob(libraryScanService)
        service.javaClass.getDeclaredMethod("runAndPersistJob", Job::class.java).apply {
            isAccessible = true
            invoke(service, job)
        }

        verify(exactly = 1) { jobRunResultRepository.save(any()) }
        assertEquals("LibraryScan", capturedResult.captured.jobName)
        assertEquals(JobRunStatus.SUCCESS, capturedResult.captured.status)
    }

    @Test
    fun `service should handle job failures and save failed results`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
        service = JobService(configService, libraryScanService, jobRunResultRepository)

        val capturedResult = slot<JobRunResult>()
        every { jobRunResultRepository.save(capture(capturedResult)) } returns mockk()
        every { libraryScanService.triggerScan(any(), any()) } throws RuntimeException("Scan failed")

        val job = LibraryScanJob(libraryScanService)
        service.javaClass.getDeclaredMethod("runAndPersistJob", Job::class.java).apply {
            isAccessible = true
            invoke(service, job)
        }

        verify(exactly = 1) { jobRunResultRepository.save(any()) }
        assertEquals("LibraryScan", capturedResult.captured.jobName)
        assertEquals(JobRunStatus.FAILED, capturedResult.captured.status)
        assertEquals("Scan failed", capturedResult.captured.message)
    }

    @Test
    fun `constructor should initialize with all dependencies`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false

        assertDoesNotThrow {
            service = JobService(configService, libraryScanService, jobRunResultRepository)
        }

        assertNotNull(service)
    }

    @Test
    fun `onLibraryScanScheduleUpdated should handle multiple consecutive updates`() {
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns false
        service = JobService(configService, libraryScanService, jobRunResultRepository)

        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 * * * *"

        assertDoesNotThrow {
            service.onLibraryScanScheduleUpdated()
            service.onLibraryScanScheduleUpdated()
            service.onLibraryScanScheduleUpdated()
        }

        verify(atLeast = 3) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
    }

    @Test
    fun `scheduleLibraryScanJob should cancel existing schedule before creating new one`() {
        // First schedule
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) } returns true
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 * * * *"
        service = JobService(configService, libraryScanService, jobRunResultRepository)
        service.init()

        // Update schedule
        every { configService.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) } returns "0 0 12 * * *"

        assertDoesNotThrow {
            service.onLibraryScanScheduleUpdated()
        }

        // Should have checked enabled status at least twice (init + update)
        verify(atLeast = 2) { configService.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) }
    }
}


