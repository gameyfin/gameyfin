package org.gameyfin.app.system.backup

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import reactor.test.StepVerifier
import java.time.Duration

class BackupProgressTrackerTest {

    @Test
    fun `late subscribers receive the latest state of each backup`() {
        val tracker = BackupProgressTracker()
        val finished = tracker.start(BackupTrigger.MANUAL, includesMedia = true)
        repeat(5000) { tracker.update(finished.backupId, BackupStep("Exporting images", it, 5000)) }
        tracker.complete(finished.backupId, "backup.zip", 42)
        val running = tracker.start(BackupTrigger.PRE_RESTORE, includesMedia = false)

        val received = tracker.subscribe().blockFirst(Duration.ofSeconds(5))!!

        assertEquals(2, received.size)
        val completed = received.single { it.backupId == finished.backupId }
        assertEquals(BackupTaskStatus.COMPLETED, completed.status)
        assertEquals("backup.zip", completed.backupName)
        assertEquals(42, completed.sizeBytes)
        assertNotNull(completed.finishedAt)
        assertEquals(BackupTaskStatus.IN_PROGRESS, received.single { it.backupId == running.backupId }.status)
    }

    @Test
    fun `subscribers receive live updates until the backup has finished`() {
        val tracker = BackupProgressTracker()
        lateinit var progress: BackupProgress

        StepVerifier.create(tracker.subscribe().flatMapIterable { it })
            .then {
                progress = tracker.start(BackupTrigger.MANUAL, includesMedia = false)
                tracker.update(progress.backupId, BackupStep("Exporting database tables", 1, 2))
                tracker.fail(progress.backupId, "Disk full")
            }
            .thenConsumeWhile { it.status == BackupTaskStatus.IN_PROGRESS }
            .consumeNextWith {
                assertEquals(progress.backupId, it.backupId)
                assertEquals(BackupTaskStatus.FAILED, it.status)
                assertEquals("Disk full", it.errorMessage)
            }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `updates within the same step are rate limited`() {
        val tracker = BackupProgressTracker(minEmitInterval = Duration.ofHours(1))
        val emitted = mutableListOf<BackupProgress>()
        val subscription = tracker.subscribe().subscribe { emitted.addAll(it) }

        val progress = tracker.start(BackupTrigger.MANUAL, includesMedia = true)
        (1..100).forEach { tracker.update(progress.backupId, BackupStep("Exporting images", it, 100)) }
        tracker.update(progress.backupId, BackupStep("Finishing backup"))

        Thread.sleep(1500)
        subscription.dispose()

        // Step changes and the last update of a step (current == total) are always emitted
        assertEquals(
            listOf("Preparing backup" to null, "Exporting images" to 1, "Exporting images" to 100, "Finishing backup" to null),
            emitted.map { it.currentStep.description to it.currentStep.current }
        )
        // The latest state is always kept, even if it has not been emitted
        assertEquals("Finishing backup", tracker.get(progress.backupId)!!.currentStep.description)
    }

    @Test
    fun `finished backups are evicted after the retention period`() {
        val tracker = BackupProgressTracker(retention = Duration.ZERO)
        val finished = tracker.start(BackupTrigger.MANUAL, includesMedia = false)
        tracker.complete(finished.backupId, "backup.zip", 1)
        val running = tracker.start(BackupTrigger.MANUAL, includesMedia = false)
        val stillRunning = tracker.start(BackupTrigger.MANUAL, includesMedia = false)

        assertNull(tracker.get(finished.backupId))
        assertNotNull(tracker.get(running.backupId))
        assertNotNull(tracker.get(stillRunning.backupId))
    }
}
