package org.gameyfin.app.core.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.gameyfin.app.core.download.bandwidth.SessionBandwidthManager
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prometheus metrics for game downloads.
 *
 * Exported metrics:
 * - `gameyfin_downloads_started_total`       – counter of downloads started (tags: throttled)
 * - `gameyfin_downloads_completed_total`     – counter of downloads completed successfully
 * - `gameyfin_downloads_failed_total`        – counter of downloads that failed / were cancelled
 * - `gameyfin_downloads_active`              – gauge of currently active downloads
 * - `gameyfin_downloads_bytes_total`         – counter of total bytes streamed to clients
 * - `gameyfin_downloads_active_sessions`     – gauge of sessions with at least one active download
 * - `gameyfin_downloads_bandwidth_bytes_per_second` – gauge of aggregate current bandwidth across all sessions
 */
@Component
class DownloadMetrics(
    registry: MeterRegistry,
    sessionBandwidthManager: SessionBandwidthManager
) {

    private val activeDownloads = AtomicInteger(0)

    private val downloadsStartedThrottled: Counter = Counter.builder("gameyfin.downloads.started")
        .description("Total number of downloads started")
        .tag("throttled", "true")
        .register(registry)

    private val downloadsStartedUnthrottled: Counter = Counter.builder("gameyfin.downloads.started")
        .description("Total number of downloads started")
        .tag("throttled", "false")
        .register(registry)

    private val downloadsCompleted: Counter = Counter.builder("gameyfin.downloads.completed")
        .description("Total number of downloads completed successfully")
        .register(registry)

    private val downloadsFailed: Counter = Counter.builder("gameyfin.downloads.failed")
        .description("Total number of downloads that failed or were cancelled")
        .register(registry)

    private val bytesTransferred: Counter = Counter.builder("gameyfin.downloads.bytes")
        .description("Total bytes streamed to clients")
        .baseUnit("bytes")
        .register(registry)

    init {
        registry.gauge("gameyfin.downloads.active", activeDownloads) { it.get().toDouble() }

        registry.gauge("gameyfin.downloads.active.sessions", sessionBandwidthManager) {
            it.getStats().values.count { s -> s.activeDownloads > 0 }.toDouble()
        }

        registry.gauge("gameyfin.downloads.bandwidth.bytes.per.second", sessionBandwidthManager) {
            it.getStats().values.sumOf { s -> s.currentBytesPerSecond }.toDouble()
        }
    }

    /** Call when a download starts. */
    fun recordDownloadStarted(throttled: Boolean) {
        activeDownloads.incrementAndGet()
        if (throttled) downloadsStartedThrottled.increment() else downloadsStartedUnthrottled.increment()
    }

    /** Call when a download completes successfully. */
    fun recordDownloadCompleted(bytesWritten: Long) {
        activeDownloads.decrementAndGet()
        downloadsCompleted.increment()
        bytesTransferred.increment(bytesWritten.toDouble())
    }

    /** Call when a download fails or is cancelled by the client. */
    fun recordDownloadFailed() {
        activeDownloads.decrementAndGet()
        downloadsFailed.increment()
    }
}


