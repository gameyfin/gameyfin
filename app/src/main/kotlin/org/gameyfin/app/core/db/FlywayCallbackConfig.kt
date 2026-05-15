package org.gameyfin.app.core.db

import org.flywaydb.core.api.callback.Callback
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FlywayCallbackConfig {

    @Bean
    fun clearLegacyChecksumsCallback(): Callback = ClearLegacyChecksumsCallback()
}