package org.gameyfin.app.core.config

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.gameyfin.app.media.Image
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfiguration {

    /**
     * Cache for Image entities keyed by ID.
     */
    @Bean
    fun imageCache(): Cache<Long, Image> {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .build()
    }
}

