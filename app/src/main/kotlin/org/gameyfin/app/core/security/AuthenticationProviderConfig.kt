package org.gameyfin.app.core.security

import org.gameyfin.app.users.UserService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class AuthenticationProviderConfig {
    @Bean
    fun hierarchicalUserAuthenticationProvider(
        userService: UserService,
        roleHierarchy: RoleHierarchy,
        passwordEncoder: PasswordEncoder
    ): DaoAuthenticationProvider {
        val provider = DaoAuthenticationProvider()
        provider.setUserDetailsService(userService)
        provider.setPasswordEncoder(passwordEncoder)
        provider.setAuthoritiesMapper(RoleHierarchyAuthoritiesMapper(roleHierarchy))
        return provider
    }
}
