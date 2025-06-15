package org.gameyfin.app.media

import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.games.entities.ImageType
import org.gameyfin.app.games.repositories.ImageContentStore
import org.apache.tika.Tika
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.games.repositories.ImageRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageContentStore: ImageContentStore
) {
    companion object {
        private val tika = Tika();
    }

    fun downloadIfNew(image: Image) {
        if (image.originalUrl == null) throw IllegalArgumentException("Image must have an original URL")

        imageRepository.findByOriginalUrl(image.originalUrl)?.let {
            imageContentStore.associate(image, it.contentId)
            return
        }

        TikaInputStream.get { image.originalUrl.openStream() }.use { input ->
            image.mimeType = tika.detect(input)
            imageContentStore.setContent(image, input)
        }
    }

    fun createFile(type: ImageType, content: InputStream, mimeType: String): Image {
        val image = Image(type = type, mimeType = mimeType)
        imageRepository.save(image)
        return imageContentStore.setContent(image, content)
    }

    fun getImage(id: Long): Image? {
        return imageRepository.findByIdOrNull(id)
    }

    fun getFileContent(id: Long): InputStream? {
        val image = getImage(id) ?: return null
        return getFileContent(image)
    }

    fun getFileContent(image: Image): InputStream? {
        return imageContentStore.getContent(image)

    }

    fun deleteFile(image: Image) {
        imageContentStore.unsetContent(image)
        imageRepository.delete(image)
    }

    fun updateFileContent(image: Image, content: InputStream, mimeType: String? = null): Image {
        mimeType?.let { image.mimeType = it }
        imageRepository.save(image)
        return imageContentStore.setContent(image, content)
    }
}