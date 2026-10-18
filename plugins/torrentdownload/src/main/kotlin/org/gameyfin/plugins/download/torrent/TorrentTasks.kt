package org.gameyfin.plugins.download.torrent

import org.gameyfin.plugins.download.torrent.TorrentDownloadPlugin.Companion.isUsableTorrentFile
import org.gameyfin.plugins.download.torrent.TorrentDownloadPlugin.Companion.lockFor
import org.gameyfin.plugins.download.torrent.TorrentDownloadPlugin.Companion.plugin
import org.gameyfin.plugins.download.torrent.TorrentDownloadPlugin.Companion.state
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.GameSelection
import org.gameyfin.pluginapi.tasks.GameyfinTask
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.pf4j.Extension
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.measureTimedValue

private fun Path.normalized(): Path = toAbsolutePath().normalize()

/**
 * Removes torrent files which can no longer be used, because their game files have been removed or changed,
 * the game is no longer part of any library or the torrent announces an outdated tracker URL.
 */
@Extension
class CleanInvalidTorrentsTask : GameyfinTask {
    private val log = LoggerFactory.getLogger(CleanInvalidTorrentsTask::class.java)

    override val key = "clean-invalid-torrents"
    override val name = "Clean invalid torrents"
    override val description =
        "Removes torrent files that no longer have corresponding game files or are outdated, " +
                "and stops seeding them."
    override val defaultSchedule = "0 0 5 * * *"

    override val settings = listOf(
        ConfigMetadata(
            key = "removeOutdated",
            label = "Remove outdated torrents",
            description = "Also remove torrents of games whose files have been modified after the torrent was created. " +
                    "This requires checking the modification time of every file of every game.",
            type = Boolean::class.java,
            default = true
        )
    )

    override fun execute(context: TaskContext): TaskResult {
        val removeOutdated = context.setting<Boolean>("removeOutdated") ?: true
        val trackerUrl = plugin.getTrackerUri().toString()
        val knownGamePaths = plugin.dataService.getGames().map { it.path.normalized() }.toSet()

        val entries = synchronized(state) { state.torrentFilesMetadata.toList() }
        val removalReasons = mutableMapOf<String, Int>()

        log.info("Checking ${entries.size} torrents")
        entries.forEachIndexed { index, entry ->
            context.throwIfCancelled()
            context.progress("Checking torrents", index, entries.size)

            synchronized(lockFor(entry.torrentFile)) {
                val reason = try {
                    invalidityReason(entry, trackerUrl, knownGamePaths, removeOutdated)
                } catch (e: Exception) {
                    log.warn("Could not check torrent '${entry.torrentFile.name}', keeping it", e)
                    null
                }
                if (reason != null) {
                    log.info("Removing torrent '${entry.torrentFile.name}' of '${entry.gameFile}': $reason")
                    remove(entry)
                    removalReasons.merge(reason, 1, Int::plus)
                }
            }
        }

        context.throwIfCancelled()
        context.progress("Removing orphaned torrent files")
        val orphans = removeOrphanedTorrentFiles()

        val removed = removalReasons.values.sum()
        log.info("Removed $removed invalid torrents and $orphans orphaned torrent files, ${entries.size - removed} torrents are valid")
        removalReasons.forEach { (reason, count) -> log.info("  $count: $reason") }

        return TaskResult("Removed $removed invalid torrents and $orphans orphaned torrent files, kept ${entries.size - removed}")
    }

    private fun invalidityReason(
        entry: TorrentFileMetadata,
        trackerUrl: String,
        knownGamePaths: Set<Path>,
        removeOutdated: Boolean
    ): String? = when {
        !isUsableTorrentFile(entry.torrentFile, expectedTrackerUrl = trackerUrl) ->
            "torrent file is missing, unreadable or announces an outdated tracker URL"

        !Files.exists(entry.gameFile) -> "game files no longer exist"
        entry.gameFile.normalized() !in knownGamePaths -> "game is no longer part of any library"
        removeOutdated && plugin.isOutdated(entry.gameFile, entry.torrentFile) -> "game files have been modified"
        else -> null
    }

    private fun remove(entry: TorrentFileMetadata) {
        if (Files.exists(entry.torrentFile)) {
            plugin.removeFromSession(entry.torrentFile)
            entry.torrentFile.deleteIfExists()
        }
        synchronized(state) {
            state.torrentFilesMetadata.remove(entry)
            plugin.saveState(state)
        }
    }

    /**
     * Deletes torrent files in the data directory which are not tracked (e.g. left behind by older versions).
     * Temporary files of running creations (*.torrent.tmp) are not touched.
     */
    private fun removeOrphanedTorrentFiles(): Int {
        var removed = 0
        plugin.dataDirectory.listDirectoryEntries("*.torrent").forEach { torrentFile ->
            synchronized(lockFor(torrentFile)) {
                val tracked = synchronized(state) {
                    state.torrentFilesMetadata.any { it.torrentFile.normalized() == torrentFile.normalized() }
                }
                if (!tracked) {
                    log.info("Removing orphaned torrent file '${torrentFile.name}'")
                    if (isUsableTorrentFile(torrentFile)) plugin.removeFromSession(torrentFile)
                    if (torrentFile.deleteIfExists()) removed++
                }
            }
        }
        return removed
    }
}

/**
 * Generates (or updates) the torrent files of games in advance, so downloads can start without waiting
 * for the torrent file to be generated.
 */
@Extension
class PregenerateTorrentsTask : GameyfinTask {
    private val log = LoggerFactory.getLogger(PregenerateTorrentsTask::class.java)

    override val key = "pregenerate-torrents"
    override val name = "Pre-generate torrent files"
    override val description =
        "Pre-generates or updates the torrent files of games. " +
                "This improves the download performance by avoiding delays caused by on-demand generation."
    override val defaultSchedule = "0 0 3 * * *"
    override val scheduledByDefault = false

    override val settings = listOf(
        ConfigMetadata(
            key = "regenerateOutdated",
            label = "Regenerate outdated torrents",
            description = "Regenerate existing torrent files if the game files have been modified after the torrent was created",
            type = Boolean::class.java,
            default = true
        )
    )

    override val parameters = listOf(
        ConfigMetadata(
            key = "games",
            label = "Games",
            description = "Games to generate torrent files for. Leave empty to process all games.",
            type = GameSelection::class.java,
            isRequired = false
        )
    )

    override fun execute(context: TaskContext): TaskResult {
        val regenerateOutdated = context.setting<Boolean>("regenerateOutdated") ?: true
        val selection = context.parameter<GameSelection>("games")

        val games = if (selection == null || selection.isEmpty()) {
            plugin.dataService.getGames()
        } else {
            plugin.dataService.getGames(selection.gameIds).also { found ->
                val missing = selection.gameIds - found.map { it.id }.toSet()
                if (missing.isNotEmpty()) log.warn("Games with IDs ${missing.joinToString()} do not exist")
            }
        }

        log.info("Generating torrent files for ${games.size} games")
        val results = mutableMapOf<TorrentGenerationResult, Int>()
        var skipped = 0
        var failed = 0

        games.forEachIndexed { index, game ->
            context.throwIfCancelled()
            val title = game.title ?: game.path.name
            context.progress("Generating torrent for '$title'", index, games.size)

            if (!Files.exists(game.path)) {
                log.warn("Skipping '$title': game files '${game.path}' do not exist")
                skipped++
                return@forEachIndexed
            }

            try {
                val (result, duration) = measureTimedValue { plugin.ensureTorrent(game.path, regenerateOutdated) }
                when (result) {
                    TorrentGenerationResult.CREATED -> log.info("Created torrent for '$title' in ${duration.asHumanReadable()}")
                    TorrentGenerationResult.UPDATED -> log.info("Updated torrent for '$title' in ${duration.asHumanReadable()}")
                    TorrentGenerationResult.UP_TO_DATE -> log.debug("Torrent for '$title' is up-to-date")
                }
                results.merge(result, 1, Int::plus)
            } catch (e: Exception) {
                log.error("Failed to generate torrent for '$title'", e)
                failed++
            }
        }

        val summary = buildString {
            append("Created ${results[TorrentGenerationResult.CREATED] ?: 0}, ")
            append("updated ${results[TorrentGenerationResult.UPDATED] ?: 0}, ")
            append("${results[TorrentGenerationResult.UP_TO_DATE] ?: 0} up-to-date")
            if (skipped > 0) append(", $skipped skipped")
            if (failed > 0) append(", $failed failed")
        }
        log.info(summary)

        if (failed > 0) error("$summary (see log for details)")
        return TaskResult(summary)
    }
}
