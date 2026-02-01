package org.gameyfin.app.core.config

import org.gameyfin.app.core.interceptors.EntityUpdateInterceptor
import org.hibernate.cfg.AvailableSettings
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JpaConfiguration {

    @Bean
    fun hibernatePropertiesCustomizer(entityUpdateInterceptor: EntityUpdateInterceptor): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { hibernateProperties ->
            hibernateProperties[AvailableSettings.INTERCEPTOR] = entityUpdateInterceptor
        }
    }
}
