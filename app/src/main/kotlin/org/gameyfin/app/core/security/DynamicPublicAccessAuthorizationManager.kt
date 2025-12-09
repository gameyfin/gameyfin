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

    @Deprecated("Deprecated in superclass")
    override fun check(
        authentication: Supplier<Authentication?>?,
        `object`: RequestAuthorizationContext?
    ): AuthorizationDecision {
        val auth = authentication?.get()
        val allow = (auth?.isAuthenticated == true && auth.principal != "anonymousUser") ||
                config.get(ConfigProperties.Security.AllowPublicAccess) == true
        return AuthorizationDecision(allow)
    }

    override fun authorize(
        authentication: Supplier<Authentication?>?,
        `object`: RequestAuthorizationContext?
    ): AuthorizationResult {
        @Suppress("DEPRECATION")
        return check(authentication, `object`)
    }
}