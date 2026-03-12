package org.gameyfin.app.core.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.gameyfin.app.libraries.enums.ScanType
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Prometheus metrics for library scanning.
 *
 * Exported metrics:
 * - `gameyfin_scans_started_total`        – counter of scans started (tags: type)
 * - `gameyfin_scans_completed_total`      – counter of scans completed (tags: type)
 * - `gameyfin_scans_failed_total`         – counter of scans that failed (tags: type)
 * - `gameyfin_scans_active`               – gauge of currently running scans
 * - `gameyfin_scans_duration_seconds`     – timer of scan duration (tags: type)
 * - `gameyfin_scans_games_new_total`      – counter of newly matched games
 * - `gameyfin_scans_games_removed_total`  – counter of removed games
 * - `gameyfin_scans_games_updated_total`  – counter of updated games
 * - `gameyfin_scans_games_unmatched_total`– counter of unmatched paths
 */
@Component
class ScanMetrics(private val registry: MeterRegistry) {

    private val activeScans = AtomicInteger(0)

    // Pre-register per-type counters & timers
    private val scansStarted = ScanType.entries.associateWith { type ->
        Counter.builder("gameyfin.scans.started")
            .description("Total number of library scans started")
            .tag("type", type.name.lowercase())
            .register(registry)
    }

    private val scansCompleted = ScanType.entries.associateWith { type ->
        Counter.builder("gameyfin.scans.completed")
            .description("Total number of library scans completed successfully")
            .tag("type", type.name.lowercase())
            .register(registry)
    }

    private val scansFailed = ScanType.entries.associateWith { type ->
        Counter.builder("gameyfin.scans.failed")
            .description("Total number of library scans that failed")
            .tag("type", type.name.lowercase())
            .register(registry)
    }

    private val scanDuration = ScanType.entries.associateWith { type ->
        Timer.builder("gameyfin.scans.duration")
            .description("Duration of library scans")
            .tag("type", type.name.lowercase())
            .register(registry)
    }

    private val gamesNew: Counter = Counter.builder("gameyfin.scans.games.new")
        .description("Total number of new games found during scans")
        .register(registry)

    private val gamesRemoved: Counter = Counter.builder("gameyfin.scans.games.removed")
        .description("Total number of games removed during scans")
        .register(registry)

    private val gamesUpdated: Counter = Counter.builder("gameyfin.scans.games.updated")
        .description("Total number of games updated during scans")
        .register(registry)

    private val gamesUnmatched: Counter = Counter.builder("gameyfin.scans.games.unmatched")
        .description("Total number of unmatched paths during scans")
        .register(registry)

    init {
        registry.gauge("gameyfin.scans.active", activeScans) { it.get().toDouble() }
    }

    /** Call when a scan starts. */
    fun recordScanStarted(type: ScanType) {
        activeScans.incrementAndGet()
        scansStarted.getValue(type).increment()
    }

    /** Call when a scan completes successfully. */
    fun recordScanCompleted(
        type: ScanType,
        durationMillis: Long,
        newGames: Int,
        removedGames: Int,
        unmatchedPaths: Int,
        updatedGames: Int = 0
    ) {
        activeScans.decrementAndGet()
        scansCompleted.getValue(type).increment()
        scanDuration.getValue(type).record(durationMillis, TimeUnit.MILLISECONDS)

        if (newGames > 0) gamesNew.increment(newGames.toDouble())
        if (removedGames > 0) gamesRemoved.increment(removedGames.toDouble())
        if (updatedGames > 0) gamesUpdated.increment(updatedGames.toDouble())
        if (unmatchedPaths > 0) gamesUnmatched.increment(unmatchedPaths.toDouble())
    }

    /** Call when a scan fails. */
    fun recordScanFailed(type: ScanType, durationMillis: Long) {
        activeScans.decrementAndGet()
        scansFailed.getValue(type).increment()
        scanDuration.getValue(type).record(durationMillis, TimeUnit.MILLISECONDS)
    }
}
