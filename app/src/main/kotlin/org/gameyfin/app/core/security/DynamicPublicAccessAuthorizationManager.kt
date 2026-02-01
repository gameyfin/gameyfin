package org.gameyfin.app.core.security

import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.authorization.AuthorizationResult
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import java.util.function.Supplier

class DynamicPublicAccessAuthorizationManager(
    private val config: ConfigService
) : AuthorizationManager<RequestAuthorizationContext> {
    override fun authorize(
        authentication: Supplier<out Authentication>,
        `object`: RequestAuthorizationContext
    ): AuthorizationResult {
        val auth = authentication.get()
        val allow = (auth.isAuthenticated && auth.principal != "anonymousUser") ||
                config.get(ConfigProperties.Security.AllowPublicAccess) == true
        return AuthorizationDecision(allow)
    }
}