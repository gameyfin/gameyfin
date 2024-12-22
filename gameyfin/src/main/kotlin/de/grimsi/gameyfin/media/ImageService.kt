package de.grimsi.gameyfin.media

import de.grimsi.gameyfin.games.entities.Image
import de.grimsi.gameyfin.games.entities.ImageType
import de.grimsi.gameyfin.games.repositories.ImageContentStore
import de.grimsi.gameyfin.games.repositories.ImageRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.io.InputStream

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageContentStore: ImageContentStore
) {

    fun getImage(id: Long): Image? {
        return imageRepository.findByIdOrNull(id)
    }

    fun createFile(type: ImageType, content: InputStream, mimeType: String): Image {
        val image = Image(type = type, mimeType = mimeType)
        imageRepository.save(image)
        return imageContentStore.setContent(image, content)
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