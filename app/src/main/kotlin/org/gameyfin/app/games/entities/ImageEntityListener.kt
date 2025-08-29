package org.gameyfin.app.games.entities

import jakarta.persistence.PostRemove
import org.gameyfin.app.media.ImageService
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class ImageEntityListener : ApplicationContextAware {
    companion object {
        private lateinit var applicationContext: ApplicationContext
    }

    override fun setApplicationContext(context: ApplicationContext) {
        applicationContext = context
    }

    private fun getImageService(): ImageService {
        return applicationContext.getBean(ImageService::class.java)
    }

    @PostRemove
    fun deleted(image: Image) {
        getImageService().deleteFile(image)
    }
}