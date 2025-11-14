package org.gameyfin.app.core.download.bandwidth

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@Service
class BandwidthMonitoringService(
    private val sessionBandwidthManager: SessionBandwidthManager
) {
    private var previousStats: List<SessionStatsDto> = emptyList()

    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private var bandwidthUpdates = Sinks.many().multicast().onBackpressureBuffer<List<SessionStatsDto>>(1024, false)

        fun subscribe(): Flux<List<List<SessionStatsDto>>> {
            log.debug { "New subscription for bandwidthUpdates (#${bandwidthUpdates.currentSubscriberCount()})" }
            return bandwidthUpdates.asFlux()
                .buffer(1.seconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to bandwidthUpdates [${bandwidthUpdates.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from bandwidthUpdates with signal type $it [${bandwidthUpdates.currentSubscriberCount()}]" }
                }
        }

        fun emit(stats: List<SessionStatsDto>) {
            bandwidthUpdates.tryEmitNext(stats)
        }

        /**
         * Reset the sink - useful for testing to ensure test isolation
         */
        internal fun resetSink() {
            bandwidthUpdates = Sinks.many().multicast().onBackpressureBuffer(1024, false)
        }
    }

    /**
     * Emit bandwidth statistics every second (1000ms)
     */
    @Scheduled(fixedRate = 1000)
    fun calculateAndEmitBandwidthUpdates() {
        // First, record bandwidth snapshots for all active sessions
        sessionBandwidthManager.recordAllBandwidthSnapshots()

        val stats = sessionBandwidthManager.getStats().values.toDtos()

        // Only emit if stats have changed
        if (stats != previousStats) {
            if (stats.isNotEmpty()) {
                log.trace { "Emitting bandwidth stats for ${stats.size} active sessions" }
            } else {
                log.trace { "Emitting empty bandwidth stats (all sessions cleared)" }
            }
            emit(stats)
            previousStats = stats
        }
    }

    /**
     * Get bandwidth statistics for all active sessions
     */
    fun getActiveSessions(): List<SessionStatsDto> {
        return sessionBandwidthManager.getStats().values.toDtos()
    }

    /**
     * Clear a specific session's bandwidth tracker
     * (useful for testing or if a session is stuck)
     */
    fun clearSession(sessionId: String) {
        sessionBandwidthManager.removeTracker(sessionId)
    }
}