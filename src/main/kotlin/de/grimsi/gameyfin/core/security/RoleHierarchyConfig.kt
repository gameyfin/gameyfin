package de.grimsi.gameyfin.core.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl

@Configuration
class RoleHierarchyConfig {
    @Bean
    fun roleHierarchy(): RoleHierarchy {
        return RoleHierarchyImpl.fromHierarchy(
            """ROLE_SUPERADMIN > ROLE_ADMIN
              |ROLE_ADMIN > ROLE_USER""".trimMargin()
        )
    }
}