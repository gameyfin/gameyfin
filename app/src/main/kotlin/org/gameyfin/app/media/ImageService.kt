package org.gameyfin.app.media

import com.vanniktech.blurhash.BlurHash
import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageContentStore
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URI
import javax.imageio.ImageIO

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageContentStore: ImageContentStore,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private val tika = Tika()

        /**
         * Scale down image for faster blurhash calculation.
         * Blurhash doesn't need full resolution - 100px width is plenty for a good blur.
         */
        fun scaleImageForBlurhash(original: BufferedImage, maxWidth: Int = 100): BufferedImage {
            val originalWidth = original.width
            val originalHeight = original.height

            // If image is already small enough, return as-is
            if (originalWidth <= maxWidth) {
                return original
            }

            val scale = maxWidth.toDouble() / originalWidth
            val targetWidth = maxWidth
            val targetHeight = (originalHeight * scale).toInt()

            val scaled = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
            val g2d = scaled.createGraphics()

            // Use fast scaling for blurhash - quality doesn't matter much for a blur
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

            g2d.drawImage(original, 0, 0, targetWidth, targetHeight, null)
            g2d.dispose()

            return scaled
        }
    }

    @Async
    @TransactionalEventListener(
        classes = [GameDeletedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onGameDeleted(event: GameDeletedEvent) {
        val imagesToDelete = listOfNotNull(event.game.coverImage, event.game.headerImage)
            .toMutableList()
            .apply { addAll(event.game.images) }

        imagesToDelete.forEach { deleteImageIfUnused(it) }
    }

    @TransactionalEventListener(
        classes = [GameUpdatedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onGameUpdated(event: GameUpdatedEvent) {
        val imagesBeforeUpdate = listOfNotNull(event.previousState.coverImage, event.previousState.headerImage)
            .toMutableList()
            .apply { addAll(event.previousState.images) }
            .toSet()

        val imagesStillInUse = listOfNotNull(event.currentState.coverImage, event.currentState.headerImage)
            .toMutableList()
            .apply { addAll(event.currentState.images) }
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
            return imageRepository.save(image)
        }

        // Prefer a list lookup to avoid IncorrectResultSizeDataAccessException if duplicates exist pre-migration
        val existing = imageRepository.findAllByOriginalUrl(url).firstOrNull()
        if (existing != null) return existing

        return try {
            val toSave = Image(originalUrl = url, type = image.type)
            imageRepository.save(toSave)
        } catch (e: DataIntegrityViolationException) {
            // Unique (original_url) might have been inserted concurrently; fetch and return
            imageRepository.findAllByOriginalUrl(url).firstOrNull()
                ?: throw e
        }
    }

    fun downloadIfNew(image: Image) {
        if (image.originalUrl == null) throw IllegalArgumentException("Image must have an original URL")

        // Always try to get existing image first to avoid detached entity issues and duplicate lookups
        val existingImage = imageRepository.findAllByOriginalUrl(image.originalUrl).firstOrNull()

        // Check if the existing image has valid content
        val existingImageHasValidContent = (existingImage != null && imageHasValidContent(existingImage))

        // If the existing image has valid content we can just associate it instead of downloading again
        if (existingImageHasValidContent && existingImage.contentId != null) {
            // Associate existing content with the current image entity reference
            imageContentStore.associate(image, existingImage.contentId)
            image.contentId = existingImage.contentId
            image.contentLength = existingImage.contentLength
            image.mimeType = existingImage.mimeType
            return
        }

        // If no existing image or existing image has no valid content, download it
        TikaInputStream.get { URI.create(image.originalUrl).toURL().openStream() }.use { input ->
            image.mimeType = tika.detect(input)

            // Read the input stream into a byte array so we can use it twice
            val imageBytes = input.readBytes()

            // Calculate blurhash
            ByteArrayInputStream(imageBytes).use { blurhashStream ->
                image.blurhash = calculateBlurhash(blurhashStream)
            }

            // Store content
            ByteArrayInputStream(imageBytes).use { contentStream ->
                imageContentStore.setContent(image, contentStream)
            }
        }

        // Save or update the image to ensure it's persisted
        try {
            imageRepository.save(image)
        } catch (_: DataIntegrityViolationException) {
            // If another thread saved the same URL meanwhile, just ignore and proceed
        }
    }

    fun createFromInputStream(type: ImageType, content: InputStream, mimeType: String): Image {
        val image = Image(type = type, mimeType = mimeType)

        // Read the input stream into a byte array so we can use it twice
        val imageBytes = content.readBytes()

        // Calculate blurhash
        ByteArrayInputStream(imageBytes).use { blurhashStream ->
            image.blurhash = calculateBlurhash(blurhashStream)
        }

        // Store content
        ByteArrayInputStream(imageBytes).use { contentStream ->
            imageContentStore.setContent(image, contentStream)
        }

        // Save with blurhash
        return imageRepository.save(image)
    }

    fun getImage(id: Long): Image? {
        return imageRepository.findByIdOrNull(id)
    }

    fun getFileContent(image: Image): InputStream? {
        return imageContentStore.getContent(image)

    }

    fun deleteImageIfUnused(image: Image) {
        val imageId = image.id ?: return

        val isImageStillInUse = gameRepository.existsByImage(imageId) || userRepository.existsByAvatar(imageId)

        if (!isImageStillInUse) {
            imageRepository.delete(image)
            imageContentStore.unsetContent(image)
        }
    }

    fun updateFileContent(image: Image, content: InputStream, mimeType: String? = null): Image {
        mimeType?.let { image.mimeType = it }

        // Read the input stream into a byte array so we can use it twice
        val imageBytes = content.readBytes()

        // Calculate blurhash
        ByteArrayInputStream(imageBytes).use { blurhashStream ->
            image.blurhash = calculateBlurhash(blurhashStream)
        }

        // Store content
        ByteArrayInputStream(imageBytes).use { contentStream ->
            imageContentStore.setContent(image, contentStream)
        }

        // Save with blurhash
        return imageRepository.save(image)
    }

    private fun imageHasValidContent(image: Image): Boolean {
        val imageContent = imageContentStore.getContent(image)
        return imageContent != null && image.contentLength != null && image.contentLength!! > 0
    }

    private fun calculateBlurhash(inputStream: InputStream): String? {
        return try {
            val originalImage = ImageIO.read(inputStream)
            if (originalImage != null) {
                // Scale down for much faster processing
                val scaledImage = scaleImageForBlurhash(originalImage)

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
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
