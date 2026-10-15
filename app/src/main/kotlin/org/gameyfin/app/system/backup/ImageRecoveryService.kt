package org.gameyfin.app.system.backup

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Restores images which are missing after a backup has been restored (e.g. because the backup did not include the
 * media files) by downloading them again from their original URL.
 * Images which are no longer available are removed, including all references to them (game covers, headers,
 * screenshots and user avatars).
 *
 * The recovery runs in the background after startup if it has been scheduled by [PendingRestoreExecutor].
 * If Gameyfin is stopped in the meantime, it is resumed on the next startup.
 */
@Service
class ImageRecoveryService(
    private val imageRepository: ImageRepository,
    private val imageService: ImageService,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    transactionManager: PlatformTransactionManager,
    @Value($$"${gameyfin.backup.directory:./backups}") backupDirectory: String,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        private const val PARALLEL_DOWNLOADS = 4
        private val MIN_EMIT_INTERVAL: Duration = Duration.ofMillis(250)

        /** HTTP status codes indicating that an image does not exist anymore */
        private val UNAVAILABLE_STATUS_CODES = setOf(404, 410)
    }

    private enum class RecoveryResult { RESTORED, UNAVAILABLE, FAILED }

    private val storage = BackupStorage(Path.of(backupDirectory))
    private val transactionTemplate = TransactionTemplate(transactionManager)
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build()
    private val running = AtomicBoolean(false)

    private val progressLock = Any()
    private var latestProgress: ImageRecoveryProgress? = null
    private var lastEmittedAt = 0L

    // Events are not buffered while nobody is subscribed, new subscribers receive the current snapshot instead
    private val progressEvents = Sinks.many().multicast().directBestEffort<ImageRecoveryProgress>()

    fun subscribeToProgressEvents(): Flux<List<ImageRecoveryProgress>> {
        // Subscribe to live updates before taking the snapshot, so no update can get lost in between
        val snapshot = Mono.defer { Mono.justOrEmpty(synchronized(progressLock) { latestProgress }) }
        return Flux.merge(progressEvents.asFlux(), snapshot).buffer(Duration.ofSeconds(1))
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        if (storage.isImageRecoveryPending()) start()
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    /**
     * Starts the recovery of missing images in the background, unless it is already running.
     */
    fun start() {
        if (!running.compareAndSet(false, true)) return
        try {
            executor.execute {
                try {
                    run()
                } finally {
                    running.set(false)
                }
            }
        } catch (_: RejectedExecutionException) {
            running.set(false)
        }
    }

    private fun run() {
        synchronized(progressLock) { latestProgress = null }
        try {
            recoverMissingImages()
            storage.clearPendingImageRecovery()
        } catch (_: InterruptedException) {
            log.info { "Recovery of missing images has been interrupted, it will be resumed on the next start" }
        } catch (e: Exception) {
            if (Thread.currentThread().isInterrupted) {
                log.info { "Recovery of missing images has been interrupted, it will be resumed on the next start" }
                return
            }
            log.error(e) { "Recovery of missing images failed, it will be retried on the next start" }
            val failure = { progress: ImageRecoveryProgress ->
                progress.copy(
                    status = BackupTaskStatus.FAILED,
                    currentStep = BackupStep("Restoring images failed"),
                    finishedAt = Instant.now(),
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
            }
            synchronized(progressLock) {
                // The failure may occur before any progress has been published
                val current = latestProgress
                if (current == null) {
                    publish(failure(ImageRecoveryProgress(currentStep = BackupStep("Restoring images failed"))))
                } else {
                    update(force = true, transform = failure)
                }
            }
        }
    }

    private fun recoverMissingImages() {
        val missing = imageRepository.findAll().filterNot { imageService.hasContent(it) }
        if (missing.isEmpty()) {
            log.info { "No images are missing after the restore" }
            return
        }

        log.info { "Restoring ${missing.size} missing images" }
        publish(ImageRecoveryProgress(currentStep = BackupStep("Downloading images", 0, missing.size)))

        val restored = AtomicInteger()
        val failed = AtomicInteger()
        val processed = AtomicInteger()
        val unavailable = ConcurrentLinkedQueue<Image>()

        val permits = Semaphore(PARALLEL_DOWNLOADS)
        Executors.newVirtualThreadPerTaskExecutor().use { downloads ->
            for (image in missing) {
                permits.acquire()
                downloads.execute {
                    try {
                        when (recover(image)) {
                            RecoveryResult.RESTORED -> restored.incrementAndGet()
                            RecoveryResult.UNAVAILABLE -> unavailable.add(image)
                            RecoveryResult.FAILED -> failed.incrementAndGet()
                        }
                    } finally {
                        permits.release()
                        val done = processed.incrementAndGet()
                        update(force = done == missing.size) {
                            it.copy(
                                currentStep = BackupStep("Downloading images", done, missing.size),
                                restored = restored.get(),
                                failed = failed.get()
                            )
                        }
                    }
                }
            }
        }
        if (Thread.interrupted()) throw InterruptedException()

        var removed = 0
        if (unavailable.isNotEmpty()) {
            log.info { "Removing ${unavailable.size} images which are not available anymore" }
            // Removed sequentially, as several images may be referenced by the same game
            unavailable.forEachIndexed { index, image ->
                if (Thread.interrupted()) throw InterruptedException()
                try {
                    removeImageAndReferences(image)
                    removed++
                } catch (e: Exception) {
                    log.warn(e) { "Could not remove unavailable image ${image.id}" }
                    failed.incrementAndGet()
                }
                update(force = index == 0) {
                    it.copy(
                        currentStep = BackupStep("Removing unavailable images", index + 1, unavailable.size),
                        removed = removed,
                        failed = failed.get()
                    )
                }
            }
        }

        update(force = true) {
            it.copy(
                status = BackupTaskStatus.COMPLETED,
                currentStep = BackupStep("Images restored"),
                finishedAt = Instant.now(),
                restored = restored.get(),
                removed = removed,
                failed = failed.get()
            )
        }
        log.info {
            "Recovery of missing images finished: ${restored.get()} restored, $removed removed, ${failed.get()} failed"
        }
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

    private fun publish(progress: ImageRecoveryProgress) {
        synchronized(progressLock) {
            latestProgress = progress
            lastEmittedAt = System.nanoTime()
            progressEvents.tryEmitNext(progress)
        }
    }

    /** Updates the current progress. Updates are rate-limited unless [force] is set. */
    private fun update(force: Boolean, transform: (ImageRecoveryProgress) -> ImageRecoveryProgress) {
        synchronized(progressLock) {
            val updated = transform(latestProgress ?: return)
            latestProgress = updated
            val now = System.nanoTime()
            if (force || now - lastEmittedAt >= MIN_EMIT_INTERVAL.toNanos()) {
                lastEmittedAt = now
                progressEvents.tryEmitNext(updated)
            }
        }
    }
}
