package org.gameyfin.app.core.download.bandwidth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled tasks for bandwidth management maintenance
 */
@Component
class BandwidthMaintenanceScheduler(
    private val sessionBandwidthManager: SessionBandwidthManager
) {
    companion object {
        private val log = KotlinLogging.logger { }
    }

    /**
     * Clean up inactive bandwidth trackers every 10 minutes
     */
    @Scheduled(fixedRate = 600_000) // 10 minutes
    fun cleanupInactiveTrackers() {
        log.debug { "Running bandwidth tracker cleanup..." }
        val statsBefore = sessionBandwidthManager.getStats()
        sessionBandwidthManager.cleanupInactiveTrackers()
        val statsAfter = sessionBandwidthManager.getStats()

        val removed = statsBefore.size - statsAfter.size
        if (removed > 0) {
            log.debug { "Cleaned up $removed inactive bandwidth tracker(s)" }
        }
    }
}

