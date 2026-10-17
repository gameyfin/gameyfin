package org.gameyfin.app.core.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.tasks.TaskAction
import org.gameyfin.app.core.tasks.TaskAlreadyRunningException
import org.gameyfin.app.core.tasks.TaskDefinition
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.app.core.tasks.TaskRunService
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.tasks.TaskCancelledException
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.FileNotFoundException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * Restores images which are missing after a backup has been restored (e.g. because the backup did not include the
 * media files) by downloading them again from their original URL.
 * Images which are no longer available are removed, including all references to them (game covers, headers,
 * screenshots and user avatars).
 *
 * The recovery runs in the background (as run of the image recovery task) after startup if it has been scheduled by
 * [PendingRestoreExecutor]. If Gameyfin is stopped or the run is cancelled in the meantime, it is resumed on the
 * next startup.
 */
@Service
class ImageRecoveryService(
    private val imageRepository: ImageRepository,
    private val imageService: ImageService,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val taskRegistry: TaskRegistry,
    private val taskRunService: TaskRunService,
    transactionManager: PlatformTransactionManager,
    @Value($$"${gameyfin.backup.directory:./backups}") backupDirectory: String,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        const val TASK_KEY = "image-recovery"
        private const val PARALLEL_DOWNLOADS = 4

        /** HTTP status codes indicating that an image does not exist anymore */
        private val UNAVAILABLE_STATUS_CODES = setOf(404, 410)
    }

    private enum class RecoveryResult { RESTORED, UNAVAILABLE, FAILED }

    private val storage = BackupStorage(Path.of(backupDirectory))
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    @PostConstruct
    fun registerTask() {
        taskRegistry.register(
            TaskDefinition(
                key = TASK_KEY,
                name = "Image recovery",
                description = "Restores images which are missing after a backup has been restored by downloading " +
                        "them again. Runs automatically after a restore and is resumed on the next start if it " +
                        "has been interrupted.",
                schedulable = false,
                manuallyTriggerable = false,
                action = TaskAction.Run { recoverMissingImages(it) }
            )
        )
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (storage.isImageRecoveryPending()) start()
    }

    /**
     * Starts the recovery of missing images in the background, unless it is already running.
     */
    fun start() {
        try {
            taskRunService.start(TASK_KEY, TaskTrigger.SYSTEM, displayName = "Restoring missing images", exclusive = true) {
                val result = recoverMissingImages(it)
                storage.clearPendingImageRecovery()
                result
            }
        } catch (_: TaskAlreadyRunningException) {
            log.debug { "Recovery of missing images is already running" }
        } catch (e: IllegalStateException) {
            log.warn { "Could not start the recovery of missing images: ${e.message}" }
        }
    }

    private fun recoverMissingImages(context: TaskContext): TaskResult {
        context.progress("Searching for missing images")
        val missing = imageRepository.findAll().filterNot { imageService.hasContent(it) }
        if (missing.isEmpty()) {
            log.info { "No images are missing" }
            return TaskResult("No images are missing")
        }

        log.info { "Restoring ${missing.size} missing images" }
        context.progress("Downloading images", 0, missing.size)

        val restored = AtomicInteger()
        val failed = AtomicInteger()
        val processed = AtomicInteger()
        val unavailable = ConcurrentLinkedQueue<Image>()

        val permits = Semaphore(PARALLEL_DOWNLOADS)
        Executors.newVirtualThreadPerTaskExecutor().use { downloads ->
            for (image in missing) {
                if (context.isCancellationRequested) break
                permits.acquire()
                downloads.execute(context.wrap(Runnable {
                    try {
                        when (recover(image)) {
                            RecoveryResult.RESTORED -> restored.incrementAndGet()
                            RecoveryResult.UNAVAILABLE -> unavailable.add(image)
                            RecoveryResult.FAILED -> failed.incrementAndGet()
                        }
                    } finally {
                        permits.release()
                        context.progress("Downloading images", processed.incrementAndGet(), missing.size)
                    }
                }))
            }
        }
        if (Thread.interrupted()) throw InterruptedException()

        var removed = 0
        if (unavailable.isNotEmpty() && !context.isCancellationRequested) {
            log.info { "Removing ${unavailable.size} images which are not available anymore" }
            // Removed sequentially, as several images may be referenced by the same game
            for ((index, image) in unavailable.withIndex()) {
                if (Thread.interrupted()) throw InterruptedException()
                if (context.isCancellationRequested) break
                try {
                    removeImageAndReferences(image)
                    removed++
                } catch (e: Exception) {
                    log.warn(e) { "Could not remove unavailable image ${image.id}" }
                    failed.incrementAndGet()
                }
                context.progress("Removing unavailable images", index + 1, unavailable.size)
            }
        }

        val summary = "${restored.get()} restored, $removed removed, ${failed.get()} failed"
        if (context.isCancellationRequested) {
            log.info { "Recovery of missing images has been cancelled, it will be resumed on the next start" }
            throw TaskCancelledException(summary)
        }
        log.info { "Recovery of missing images finished: $summary" }
        return TaskResult(summary)
    }

    private fun recover(image: Image): RecoveryResult {
        val uri = image.originalUrl?.takeIf { it.isNotBlank() }?.let {
            try {
                URI.create(it)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        // Images without a (valid) original URL, e.g. uploaded avatars, cannot be restored
        if (uri?.scheme == null) return RecoveryResult.UNAVAILABLE

        return try {
            when (uri.scheme.lowercase()) {
                "http", "https" -> download(image, uri)
                else -> uri.toURL().openStream().use { input ->
                    imageService.restoreContent(image, input)
                    RecoveryResult.RESTORED
                }
            }
        } catch (_: FileNotFoundException) {
            RecoveryResult.UNAVAILABLE
        } catch (_: NoSuchFileException) {
            RecoveryResult.UNAVAILABLE
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            RecoveryResult.FAILED
        } catch (e: Exception) {
            log.warn { "Could not restore image ${image.id} from ${image.originalUrl}: ${e.message ?: e.javaClass.simpleName}" }
            log.debug(e) {}
            RecoveryResult.FAILED
        }
    }

    private fun download(image: Image, uri: URI): RecoveryResult {
        val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        response.body().use { body ->
            val status = response.statusCode()
            return when (status) {
                in 200..299 -> {
                    imageService.restoreContent(image, body)
                    RecoveryResult.RESTORED
                }

                in UNAVAILABLE_STATUS_CODES -> RecoveryResult.UNAVAILABLE
                else -> {
                    log.warn { "Could not restore image ${image.id} from $uri: HTTP status $status" }
                    RecoveryResult.FAILED
                }
            }
        }
    }

    /**
     * Removes the image and all references to it. Games are updated through JPA, so connected clients are notified.
     */
    private fun removeImageAndReferences(image: Image) {
        val imageId = image.id ?: return
        transactionTemplate.executeWithoutResult {
            gameRepository.findAllByImage(imageId).forEach { game ->
                if (game.coverImage?.id == imageId) {
                    game.coverImage = null
                    game.metadata.fields.remove("coverImage")
                }
                if (game.headerImage?.id == imageId) {
                    game.headerImage = null
                    game.metadata.fields.remove("headerImage")
                }
                if (game.images.removeIf { it.id == imageId } && game.images.isEmpty()) {
                    game.metadata.fields.remove("images")
                }
                // Changes to collections alone do not mark the game as modified
                game.updatedAt = Instant.now()
            }
            userRepository.findAllByAvatar(imageId).forEach { it.avatar = null }
        }
        imageService.deleteImageIfUnused(image)
    }
}
