package org.gameyfin.app.libraries.scan

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.ImageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

@Service
class LibraryGameProcessor(
    private val gameService: GameService,
    private val imageService: ImageService,
    private val filesystemService: FilesystemService
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processNewGame(path: Path, library: Library): Game {
        var game: Game? = null
        try {
            // Match metadata and build a Game entity (not persisted yet)
            game = gameService.matchFromFile(path, library)
                ?: throw IllegalStateException("Could not identify game at path '$path'")

            // Download all referenced images (idempotent and deduplicated in ImageService)
            downloadImagesForGame(game)

            // Calculate file size
            game.metadata.fileSize = filesystemService.calculateFileSize(game.metadata.path)

            // Persist the game
            val persisted = gameService.create(listOf(game)).first()
            return persisted
        } catch (e: Exception) {
            log.error { "Failed to process new game at '$path': ${e.message}" }
            log.debug(e) {}
            // Best-effort cleanup of any images that might have been created/downloaded
            game?.let { safeCleanupImages(it) }
            throw e
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processExistingGame(game: Game): Game? {
        // Note: GameService.update will load and save the managed entity inside this same transaction
        var updated: Game? = null
        try {
            updated = gameService.update(game)
            if (updated != null) {
                // Download any images now associated with the game
                downloadImagesForGame(updated)
                // Recalculate file size (in case files changed)
                updated.metadata.fileSize = filesystemService.calculateFileSize(updated.metadata.path)
                // No explicit save needed; entity is managed in this transaction and will be flushed on commit
            }
            return updated
        } catch (e: Exception) {
            log.error { "Failed to update game '${game.id}': ${e.message}" }
            log.debug(e) {}
            // Cleanup only the images we attempted to use for this update
            updated?.let { safeCleanupImages(it) }
            throw e
        }
    }

    private fun downloadImagesForGame(game: Game) {
        game.coverImage?.let { imageService.downloadIfNew(it) }
        game.headerImage?.let { imageService.downloadIfNew(it) }
        game.images.forEach { imageService.downloadIfNew(it) }
    }

    private fun safeCleanupImages(game: Game) {
        try {
            game.coverImage?.let { imageService.deleteImageIfUnused(it) }
            game.headerImage?.let { imageService.deleteImageIfUnused(it) }
            game.images.forEach { imageService.deleteImageIfUnused(it) }
        } catch (_: Exception) {
            // Ignore cleanup failures
        }
    }
}

