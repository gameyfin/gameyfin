package org.gameyfin.app.core.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.gameyfin.app.platforms.serialization.PlatformDeserializer
import org.gameyfin.app.platforms.serialization.PlatformSerializer
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

@Configuration
class JacksonConfiguration {

    @Bean
    fun objectMapperCustomizer(): Jackson2ObjectMapperBuilder {
        return Jackson2ObjectMapperBuilder()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .modulesToInstall(JavaTimeModule(), platformModule())
    }

    private fun platformModule(): SimpleModule {
        val module = SimpleModule("PlatformModule")
        module.addSerializer(Platform::class.java, PlatformSerializer())
        module.addDeserializer(Platform::class.java, PlatformDeserializer())
        return module
    }
}

