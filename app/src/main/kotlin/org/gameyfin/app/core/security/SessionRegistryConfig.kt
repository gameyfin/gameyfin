package org.gameyfin.app.core.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.session.HttpSessionEventPublisher

@Configuration
class SessionRegistryConfig {
    @Bean
    fun sessionRegistry(): SessionRegistry {
        return SessionRegistryImpl()
    }

    /**
     * Publishes servlet session lifecycle events (destroyed, ID changed) to the Spring context.
     * Without it, [SessionRegistryImpl] never learns about expired/invalidated sessions and keeps their entries forever.
     */
    @Bean
    fun httpSessionEventPublisher(): HttpSessionEventPublisher {
        return HttpSessionEventPublisher()
    }
}