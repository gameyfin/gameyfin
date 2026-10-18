package org.gameyfin.app.core.tasks

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.persistence.ConfigRepository
import org.gameyfin.app.core.tasks.persistence.TaskScheduleEntry
import org.gameyfin.app.core.tasks.persistence.TaskScheduleRepository
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.support.CronExpression
import org.springframework.scheduling.support.CronTrigger
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledFuture

/**
 * Schedules the execution of tasks using the Spring [TaskScheduler], based on the schedules configured by
 * administrators (or the default schedules of the tasks).
 */
@Service
class TaskSchedulerService(
    private val taskScheduler: TaskScheduler,
    private val registry: TaskRegistry,
    private val scheduleRepository: TaskScheduleRepository,
    private val runService: TaskRunService,
    private val configRepository: ConfigRepository
) {

    companion object {
        private val log = KotlinLogging.logger {}

        /** Config keys used for the schedule of library scans before the introduction of the task framework */
        internal const val LEGACY_LIBRARY_SCAN_ENABLED_KEY = "library.metadata.update.enabled"
        internal const val LEGACY_LIBRARY_SCAN_SCHEDULE_KEY = "library.metadata.update.schedule"

        fun validateCron(cron: String) {
            require(CronExpression.isValidExpression(cron)) { "Invalid cron expression: $cron" }
        }
    }

    data class Schedule(val cron: String?, val enabled: Boolean)

    private val futures = mutableMapOf<String, ScheduledFuture<*>>()

    @Volatile
    private var started = false

    /**
     * Tasks are only scheduled once the application is ready, as the definitions are registered during startup.
     */
    @Order(0)
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        migrateLegacyLibraryScanSchedule()
        started = true
        registry.getAll().forEach { schedule(it) }
    }

    @EventListener
    fun onTaskRegistered(event: TaskRegisteredEvent) {
        if (started) schedule(event.definition)
    }

    @EventListener
    fun onTaskUnregistered(event: TaskUnregisteredEvent) {
        unschedule(event.definition.key)
        runService.cancelAll(event.definition.key)
    }

    fun getSchedule(definition: TaskDefinition): Schedule {
        val entry = scheduleRepository.findById(definition.key).orElse(null)
            ?: return Schedule(definition.defaultSchedule, definition.scheduledByDefault && definition.schedulable)
        return Schedule(entry.cron ?: definition.defaultSchedule, entry.enabled && definition.schedulable)
    }

    fun nextExecution(definition: TaskDefinition): Instant? {
        if (synchronized(futures) { futures[definition.key] } == null) return null
        val cron = getSchedule(definition).cron ?: return null
        return try {
            CronExpression.parse(cron).next(ZonedDateTime.now())?.toInstant()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun updateSchedule(definition: TaskDefinition, cron: String?, enabled: Boolean) {
        require(definition.schedulable || !enabled) { "${definition.name} can not be scheduled" }
        val normalizedCron = cron?.trim()?.takeIf { it.isNotEmpty() }
        if (normalizedCron != null) validateCron(normalizedCron)
        require(!enabled || normalizedCron != null || definition.defaultSchedule != null) {
            "A schedule is required to enable the scheduled execution"
        }
        scheduleRepository.save(TaskScheduleEntry(definition.key, normalizedCron, enabled))
        schedule(definition)
    }

    private fun schedule(definition: TaskDefinition) {
        synchronized(futures) {
            futures.remove(definition.key)?.cancel(false)
            if (!definition.schedulable) return
            val (cron, enabled) = getSchedule(definition)
            if (!enabled || cron.isNullOrBlank()) {
                log.debug { "Task ${definition.key} is not scheduled" }
                return
            }
            try {
                futures[definition.key] = taskScheduler.schedule({ execute(definition.key) }, CronTrigger(cron))!!
                log.debug { "Scheduled task ${definition.key} using '$cron'" }
            } catch (e: Exception) {
                log.error { "Could not schedule task ${definition.key} using '$cron': ${e.message}" }
            }
        }
    }

    private fun unschedule(key: String) {
        synchronized(futures) {
            futures.remove(key)?.cancel(false)
        }
    }

    private fun execute(key: String) {
        try {
            runService.trigger(key, TaskTrigger.SCHEDULE)
        } catch (e: Exception) {
            log.error(e) { "Scheduled execution of task $key failed" }
        }
    }

    /**
     * Moves the schedule of library scans from the configuration into the task schedules.
     */
    private fun migrateLegacyLibraryScanSchedule() {
        try {
            val enabled = configRepository.findById(LEGACY_LIBRARY_SCAN_ENABLED_KEY).orElse(null)
            val schedule = configRepository.findById(LEGACY_LIBRARY_SCAN_SCHEDULE_KEY).orElse(null)
            if (enabled == null && schedule == null) return

            if (!scheduleRepository.existsById(LIBRARY_SCAN_TASK_KEY)) {
                val cron = schedule?.value?.trim()?.takeIf { CronExpression.isValidExpression(it) }
                scheduleRepository.save(
                    TaskScheduleEntry(LIBRARY_SCAN_TASK_KEY, cron, enabled?.value?.toBooleanStrictOrNull() ?: true)
                )
                log.info { "Migrated the schedule of library scans to the task schedules" }
            }
            enabled?.let { configRepository.delete(it) }
            schedule?.let { configRepository.delete(it) }
        } catch (e: Exception) {
            log.warn(e) { "Could not migrate the schedule of library scans" }
        }
    }
}

/** Key of the task which scans all libraries, defined in [org.gameyfin.app.libraries.LibraryScanService] */
const val LIBRARY_SCAN_TASK_KEY = "library-scan"
