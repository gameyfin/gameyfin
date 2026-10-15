package org.gameyfin.app.system.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps track of the progress of backup tasks and publishes it to subscribers
 * (using the same mechanism as the library scan progress).
 */
class BackupProgressTracker(
    private val retention: Duration = Duration.ofHours(24),
    private val minEmitInterval: Duration = Duration.ofMillis(250)
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    /** Keeps only the most recent progress per backup task */
    private val latestProgress = ConcurrentHashMap<UUID, BackupProgress>()
    private val lastEmittedAt = ConcurrentHashMap<UUID, Long>()
    // Events are not buffered while nobody is subscribed, new subscribers receive the current snapshot instead
    private val progressEvents = Sinks.many().multicast().directBestEffort<BackupProgress>()

    fun subscribe(): Flux<List<BackupProgress>> {
        // Subscribe to live updates before taking the snapshot, so no update can get lost in between
        val snapshot = Flux.defer { Flux.fromIterable(latestProgress.values.toList()) }
        return Flux.merge(progressEvents.asFlux(), snapshot)
            .buffer(Duration.ofSeconds(1))
            .doOnSubscribe { log.debug { "Subscriber added to backup progress events" } }
            .doFinally { log.debug { "Subscriber removed from backup progress events with signal type $it" } }
    }

    fun start(trigger: BackupTrigger, includesMedia: Boolean): BackupProgress {
        evictStale()
        val progress = BackupProgress(
            trigger = trigger,
            includesMedia = includesMedia,
            currentStep = BackupStep("Preparing backup")
        )
        publish(progress)
        return progress
    }

    /**
     * Updates the current step of a backup. Updates are rate-limited, except when the step changes.
     */
    fun update(backupId: UUID, step: BackupStep) {
        val previous = latestProgress[backupId] ?: return
        val updated = previous.copy(currentStep = step)
        val now = System.nanoTime()
        val last = lastEmittedAt[backupId]
        if (previous.currentStep.description != step.description || step.current == step.total ||
            last == null || now - last >= minEmitInterval.toNanos()
        ) {
            publish(updated)
        } else {
            latestProgress[backupId] = updated
        }
    }

    fun complete(backupId: UUID, backupName: String, sizeBytes: Long) {
        val previous = latestProgress[backupId] ?: return
        publish(
            previous.copy(
                status = BackupTaskStatus.COMPLETED,
                currentStep = BackupStep("Backup created"),
                finishedAt = Instant.now(),
                backupName = backupName,
                sizeBytes = sizeBytes
            )
        )
        lastEmittedAt.remove(backupId)
    }

    fun fail(backupId: UUID, errorMessage: String?) {
        val previous = latestProgress[backupId] ?: return
        publish(
            previous.copy(
                status = BackupTaskStatus.FAILED,
                currentStep = BackupStep("Backup failed"),
                finishedAt = Instant.now(),
                errorMessage = errorMessage
            )
        )
        lastEmittedAt.remove(backupId)
    }

    fun get(backupId: UUID): BackupProgress? = latestProgress[backupId]

    private fun publish(progress: BackupProgress) {
        latestProgress[progress.backupId] = progress
        lastEmittedAt[progress.backupId] = System.nanoTime()
        // Emissions may come from different threads, but the sink requires serialized calls
        synchronized(progressEvents) {
            progressEvents.tryEmitNext(progress)
        }
    }

    /** Removes tasks which have finished longer ago than the retention period */
    private fun evictStale() {
        val cutoff = Instant.now().minus(retention)
        latestProgress.values.removeIf { it.finishedAt?.isBefore(cutoff) == true }
    }
}
