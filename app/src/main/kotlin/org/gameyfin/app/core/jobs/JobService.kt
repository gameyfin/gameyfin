package org.gameyfin.app.core.jobs

import com.vaadin.hilla.exception.EndpointException
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.events.LibraryScanScheduleUpdatedEvent
import org.gameyfin.app.libraries.LibraryScanService
import org.springframework.context.event.EventListener
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ScheduledFuture

@Service
class JobService(
    private val config: ConfigService,
    libraryScanService: LibraryScanService,
    private val jobRunResultRepository: JobRunResultRepository
) {
    private val scheduler: TaskScheduler = ThreadPoolTaskScheduler().apply { initialize() }
    private var libraryScanFuture: ScheduledFuture<*>? = null

    private val libraryScanJob: Job = LibraryScanJob(libraryScanService)

    companion object {
        private val log = KotlinLogging.logger { }
    }

    @PostConstruct
    fun init() {
        scheduleLibraryScanJob()
    }

    @EventListener(LibraryScanScheduleUpdatedEvent::class)
    fun onLibraryScanScheduleUpdated() {
        scheduleLibraryScanJob()
    }

    private fun scheduleLibraryScanJob() {
        libraryScanFuture?.cancel(false)

        if (config.get(ConfigProperties.Libraries.Metadata.UpdateEnabled) != true) {
            log.debug { "Disabled scheduled library scans" }
            return
        }

        val cronExpressionString = config.get(ConfigProperties.Libraries.Metadata.UpdateSchedule) ?: return

        try {
            val cronTrigger = CronTrigger(cronExpressionString)
            libraryScanFuture = (scheduler as ThreadPoolTaskScheduler).schedule({
                runAndPersistJob(libraryScanJob)
            }, cronTrigger)
            log.debug {
                "Library scan job scheduled, next run will be @ " +
                        "${CronExpression.parse(cronExpressionString).next(LocalDateTime.now())}"
            }
        } catch (e: Exception) {
            log.error { "Failed to schedule library scan job: ${e.message}" }
            log.debug(e) { }
            throw EndpointException("Failed to schedule library scan job: ${e.message}")
        }
    }

    private fun runAndPersistJob(job: Job) {
        val result = job.run()
        jobRunResultRepository.save(result)
    }
}