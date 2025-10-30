package org.gameyfin.app.core.download.bandwidth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.hours

/**
 * Scheduled tasks for bandwidth management maintenance
 */
@Component
class BandwidthMaintenanceScheduler(
    private val sessionBandwidthManager: SessionBandwidthManager
) {
    companion object {
        private val log = KotlinLogging.logger { }

        private val INACTIVE_THRESHOLD = 24.hours
    }

    /**
     * Clean up inactive bandwidth trackers every hour
     */
    @Scheduled(cron = "@hourly")
    fun cleanupInactiveTrackers() {
        log.debug { "Running bandwidth tracker cleanup..." }
        val statsBefore = sessionBandwidthManager.getStats()
        sessionBandwidthManager.cleanupInactiveTrackers(INACTIVE_THRESHOLD)
        val statsAfter = sessionBandwidthManager.getStats()

        val removed = statsBefore.size - statsAfter.size
        if (removed > 0) {
            log.debug { "Cleaned up $removed inactive bandwidth tracker(s)" }
        }
    }
}

