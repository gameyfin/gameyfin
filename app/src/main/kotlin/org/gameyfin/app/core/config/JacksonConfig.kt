package org.gameyfin.app.core.config

import com.vaadin.hilla.EndpointController.ENDPOINT_MAPPER_FACTORY_BEAN_QUALIFIER
import com.vaadin.hilla.parser.jackson.ByteArrayModule
import com.vaadin.hilla.parser.jackson.JacksonObjectMapperFactory
import org.gameyfin.app.core.serialization.*
import org.gameyfin.pluginapi.gamemetadata.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule


/**
 * Jackson configuration for custom serializers and deserializers.
 */
@Configuration
class JacksonConfig {

    @Bean(ENDPOINT_MAPPER_FACTORY_BEAN_QUALIFIER)
    fun jsonMapperFactory(): JacksonObjectMapperFactory {
        return JacksonObjectMapperFactory {
            JsonMapper.builder()
                // Default Hilla options
                .addModule(ByteArrayModule())
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
                .enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                // Custom modules
                .addModule(displayableEnumModule())
                .build()
        }
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

