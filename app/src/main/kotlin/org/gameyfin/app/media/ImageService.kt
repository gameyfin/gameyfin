package org.gameyfin.app.media

import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.games.entities.ImageType
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageContentStore
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.io.InputStream
import java.net.URI

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageContentStore: ImageContentStore,
    private val gameRepository: GameRepository,
    private val userRepository: UserRepository
) {
    companion object {
        private val tika = Tika()
    }

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

    fun createOrGet(image: Image): Image {
        if (image.originalUrl != null) {
            imageRepository.findByOriginalUrl(image.originalUrl)?.let { return it }
        }

        return imageRepository.save(image)
    }

    fun downloadIfNew(image: Image) {
        if (image.originalUrl == null) throw IllegalArgumentException("Image must have an original URL")

        // Always try to get existing image first to avoid detached entity issues
        val existingImage = imageRepository.findByOriginalUrl(image.originalUrl)

        // Check if the existing image has valid content
        val existingImageHasValidContent = (existingImage != null && imageHasValidContent(existingImage))

        // If the existing image has valid content we can just associate it instead of downloading again
        if (existingImageHasValidContent && existingImage.contentId != null) {
            // If we have an existing image with content, associate it with the current image
            imageContentStore.associate(image, existingImage.contentId)
            // Update the current image's content metadata
            image.contentId = existingImage.contentId
            image.contentLength = existingImage.contentLength
            image.mimeType = existingImage.mimeType
            return
        }

        // If no existing image or existing image has no valid content, download it
        TikaInputStream.get { URI.create(image.originalUrl).toURL().openStream() }.use { input ->
            image.mimeType = tika.detect(input)
            imageContentStore.setContent(image, input)
        }

        // Save the image to ensure it's persisted
        imageRepository.save(image)
    }

    fun createFromInputStream(type: ImageType, content: InputStream, mimeType: String): Image {
        val image = Image(type = type, mimeType = mimeType)
        imageRepository.save(image)
        return imageContentStore.setContent(image, content)
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
        imageRepository.save(image)
        return imageContentStore.setContent(image, content)
    }

    private fun imageHasValidContent(image: Image): Boolean {
        val imageContent = imageContentStore.getContent(image)
        return imageContent != null && image.contentLength != null && image.contentLength!! > 0
    }
}