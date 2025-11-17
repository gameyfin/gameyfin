package org.gameyfin.app.core.config

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import org.gameyfin.app.core.serialization.*
import org.gameyfin.pluginapi.gamemetadata.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * Jackson configuration for custom serializers and deserializers.
 */
@Configuration
class JacksonConfig {


    @Bean
    fun objectMapperCustomizer(): Jackson2ObjectMapperBuilder {
        return Jackson2ObjectMapperBuilder()
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .modulesToInstall(JavaTimeModule(), displayableEnumModule())
    }

    fun displayableEnumModule(): SimpleModule {
        val module = SimpleModule("DisplayableEnumModule")

        // Register serializers and deserializers for enums with displayName property
        module.addSerializer(Platform::class.java, DisplayableSerializer())
        module.addDeserializer(Platform::class.java, PlatformDeserializer())

        module.addSerializer(Genre::class.java, DisplayableSerializer())
        module.addDeserializer(Genre::class.java, GenreDeserializer())

        module.addSerializer(GameFeature::class.java, DisplayableSerializer())
        module.addDeserializer(GameFeature::class.java, GameFeatureDeserializer())

        module.addSerializer(PlayerPerspective::class.java, DisplayableSerializer())
        module.addDeserializer(PlayerPerspective::class.java, PlayerPerspectiveDeserializer())

        module.addSerializer(Theme::class.java, DisplayableSerializer())
        module.addDeserializer(Theme::class.java, ThemeDeserializer())

        return module
    }
}

