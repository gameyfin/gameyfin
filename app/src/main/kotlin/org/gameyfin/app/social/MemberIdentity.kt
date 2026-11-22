package org.gameyfin.app.social

import org.gameyfin.app.api.security.ApiAuthentication
import org.gameyfin.app.core.security.LiveUserAuthentication
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component

@Component
class MemberIdentity(private val users: UserRepository) {
    fun id(auth: Authentication? = getCurrentAuth()): Long? {
        if (auth == null || !auth.isAuthenticated || AuthenticationTrustResolverImpl().isAnonymous(auth)) return null
        return when (auth) {
            is LiveUserAuthentication -> auth.userId
            is ApiAuthentication -> auth.actor.userId
            else -> {
                val principal = auth.principal
                if (principal is OidcUser) principal.subject?.let(users::findByOidcProviderId)?.id
                else users.findByUsername(auth.name)?.id
            }
        }
    }

    fun requireId(): Long = id() ?: throw AccessDeniedException("Member login required")

    fun lock(id: Long) = users.findForUpdate(id) ?: throw AccessDeniedException("Member account unavailable")
}
