package org.gameyfin.app.media

import com.github.benmanes.caffeine.cache.Cache
import com.vanniktech.blurhash.BlurHash
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.ImageIO

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val fileStorageService: FileStorageService,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository,
    private val imageCache: Cache<Long, Image>
) {
    companion object {
        private val log = KotlinLogging.logger { }

        private val tika = Tika()

        /**
         * Scale down image for faster blurhash calculation.
         * Blurhash doesn't need full resolution - 100px width is plenty for a good blur.
         */
        @Suppress("DuplicatedCode")
        fun scaleImageForBlurhash(original: BufferedImage, maxWidth: Int = 100): BufferedImage {
            val originalWidth = original.width
            val originalHeight = original.height

            // If image is already small enough, return as-is
            if (originalWidth <= maxWidth) {
                return original
            }

            val scale = maxWidth.toDouble() / originalWidth
            val targetHeight = (originalHeight * scale).toInt()

            val scaled = BufferedImage(maxWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = scaled.createGraphics()

            // Use fast scaling for blurhash - quality doesn't matter much for a blur
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

            g2d.drawImage(original, 0, 0, maxWidth, targetHeight, null)
            g2d.dispose()

            return scaled
        }
    }

    /**
     * Pre-populate the image cache at startup
     */
    @EventListener(ApplicationReadyEvent::class)
    fun prePopulateImageCache() {
        val images = imageRepository.findAll().toList()
        images.forEach { image -> image.id?.let { imageCache.put(it, image) } }
        log.debug { "Pre-populated image cache with ${images.size} entries" }
    }

    @Async
    @TransactionalEventListener(
        classes = [GameDeletedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onGameDeleted(event: GameDeletedEvent) {
        val imagesToDelete = listOfNotNull(event.game.coverImage, event.game.headerImage) +
                event.game.images

        imagesToDelete.forEach { deleteImageIfUnused(it) }
    }

    @TransactionalEventListener(
        classes = [GameUpdatedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onGameUpdated(event: GameUpdatedEvent) {
        val imagesBeforeUpdate = (listOfNotNull(event.previousState.coverImage, event.previousState.headerImage) +
                event.previousState.images)
            .toSet()

        val imagesStillInUse = (listOfNotNull(event.currentState.coverImage, event.currentState.headerImage) +
                event.currentState.images)
            .toSet()

        imagesBeforeUpdate.minus(imagesStillInUse).forEach { deleteImageIfUnused(it) }
    }

    @TransactionalEventListener(
        classes = [UserDeletedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onAccountDeleted(event: UserDeletedEvent) {
        event.user.avatar?.let { deleteImageIfUnused(it) }
    }

    @TransactionalEventListener(
        classes = [UserUpdatedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onUserUpdated(event: UserUpdatedEvent) {
        event.previousState.avatar?.let { previousAvatar ->
            if (previousAvatar != event.currentState.avatar) {
                deleteImageIfUnused(previousAvatar)
            }
        }
    }

    @Transactional
    fun createOrGet(image: Image): Image {
        val url = image.originalUrl
        if (url.isNullOrBlank()) {
            // No original URL => cannot dedupe by URL; just persist as-is
            return imageRepository.save(image).also { saved -> saved.id?.let { imageCache.put(it, saved) } }
        }

        // Prefer a list lookup to avoid IncorrectResultSizeDataAccessException if duplicates exist pre-migration
        val existing = imageRepository.findAllByOriginalUrl(url).firstOrNull()
        if (existing != null) return existing

        return try {
            val toSave = Image(originalUrl = url, type = image.type)
            imageRepository.save(toSave).also { saved -> saved.id?.let { imageCache.put(it, saved) } }
        } catch (e: DataIntegrityViolationException) {
            // Unique (original_url) might have been inserted concurrently; fetch and return
            imageRepository.findAllByOriginalUrl(url).firstOrNull()
                ?: throw e
        }
    }

    fun downloadIfNew(image: Image) {
        requireNotNull(image.originalUrl) { "Image must have an original URL" }

        // Always try to get existing image first to avoid detached entity issues and duplicate lookups
        val existingImage = imageRepository.findAllByOriginalUrl(image.originalUrl).firstOrNull()

        // Check if the existing image has valid content
        val existingImageHasValidContent = (existingImage != null && imageHasValidContent(existingImage))

        // If the existing image has valid content we can just associate it instead of downloading again
        if (existingImageHasValidContent && existingImage.contentId != null) {
            // Associate existing content with the current image entity reference
            image.contentId = existingImage.contentId
            image.contentLength = existingImage.contentLength
            image.mimeType = existingImage.mimeType
            return
        }

        // If no existing image or existing image has no valid content, download it
        TikaInputStream.get { URI.create(image.originalUrl).toURL().openStream() }.use { input ->
            image.mimeType = tika.detect(input)
            processImageContent(image, input)
        }

        // Save or update the image to ensure it's persisted
        try {
            imageRepository.save(image).also { saved -> saved.id?.let { imageCache.put(it, saved) } }
        } catch (_: DataIntegrityViolationException) {
            // If another thread saved the same URL meanwhile, just ignore and proceed
        }
    }

    fun createFromInputStream(type: ImageType, content: InputStream, mimeType: String): Image {
        val image = Image(type = type, mimeType = mimeType)
        processImageContent(image, content)
        return imageRepository.save(image).also { saved -> saved.id?.let { imageCache.put(it, saved) } }
    }

    fun getImage(id: Long): Image? {
        imageCache.getIfPresent(id)?.let { return it }
        val image = imageRepository.findByIdOrNull(id)
        if (image != null) imageCache.put(id, image)
        return image
    }

    fun getFileContent(image: Image): InputStream? {
        return fileStorageService.getFile(image.contentId)
    }

    fun getFilePath(image: Image): Path? {
        return fileStorageService.getFilePath(image.contentId)
    }

    fun deleteImageIfUnused(image: Image) {
        val imageId = image.id ?: return

        val isImageStillInUse = gameRepository.existsByImage(imageId) || userRepository.existsByAvatar(imageId)

        if (!isImageStillInUse) {
            imageCache.invalidate(imageId)
            imageRepository.delete(image)
            fileStorageService.deleteFile(image.contentId)
        }
    }

    fun updateFileContent(image: Image, content: InputStream, mimeType: String? = null): Image {
        mimeType?.let { image.mimeType = it }

        // Delete old file if it exists
        image.contentId?.let { fileStorageService.deleteFile(it) }

        // Process and store new content
        processImageContent(image, content)

        // Invalidate cache so the next read picks up fresh data
        image.id?.let { imageCache.invalidate(it) }

        return imageRepository.save(image)
    }

    private fun imageHasValidContent(image: Image): Boolean {
        return image.contentId != null
                && fileStorageService.fileExists(image.contentId)
                && image.contentLength != null
                && image.contentLength!! > 0
    }

    private fun processImageContent(image: Image, content: InputStream) {
        // Stream to a temp file to avoid holding the full image bytes on the heap.
        // This is critical during library scans where multiple images are processed
        // concurrently — buffering each one as a byte[] can easily cause OOM.
        val tempFile = Files.createTempFile("gf-img-", ".tmp")
        try {
            // 1. Write the stream to disk
            content.use { input ->
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING)
            }

            val fileSize = Files.size(tempFile)

            // 2. Calculate blurhash from the temp file
            Files.newInputStream(tempFile).use { blurhashStream ->
                image.blurhash = calculateBlurhash(blurhashStream)
            }

            // 3. Store content from the temp file
            Files.newInputStream(tempFile).use { contentStream ->
                image.contentId = fileStorageService.saveFile(contentStream)
                image.contentLength = fileSize
            }
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun calculateBlurhash(inputStream: InputStream): String? {
        return try {
            val originalImage = ImageIO.read(inputStream) ?: return null
            try {
                // Scale down for much faster processing and less memory
                val scaledImage = scaleImageForBlurhash(originalImage)
                try {
                    return if (scaledImage.width > scaledImage.height) {
                        // Landscape
                        BlurHash.encode(scaledImage, componentX = 4, componentY = 3)
                    } else if (scaledImage.width < scaledImage.height) {
                        // Portrait
                        BlurHash.encode(scaledImage, componentX = 3, componentY = 4)
                    } else {
                        // Square
                        BlurHash.encode(scaledImage, componentX = 3, componentY = 3)
                    }
                } finally {
                    // Release scaled image native memory immediately
                    if (scaledImage !== originalImage) scaledImage.flush()
                }
            } finally {
                // Release original image native memory immediately
                originalImage.flush()
            }
        } catch (_: Exception) {
            null
        }
    }
}
