package org.gameyfin.app.core.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.users.extensions.toExtendedUserInfoDto
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.filter.OncePerRequestFilter

class LiveUserSecurityFilter(
    private val users: UserRepository,
    private val hierarchy: RoleHierarchy
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val context = SecurityContextHolder.getContext()
        val auth = context.authentication
        if (auth != null && auth !is AnonymousAuthenticationToken) {
            val user = when {
                auth is LiveUserAuthentication -> users.findById(auth.userId).orElse(null)
                auth.principal is OidcUser -> (auth.principal as OidcUser).subject?.let(users::findByOidcProviderId)
                else -> users.findByUsername(auth.name)
            }
            if (auth is LiveUserAuthentication) {
                auth.refresh(user?.toExtendedUserInfoDto())
            } else if (user != null) {
                context.authentication = LiveUserAuthentication(auth, user.toExtendedUserInfoDto(), hierarchy)
                request.getSession(false)?.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context
                )
            } else {
                context.authentication = null
            }
        }
        chain.doFilter(request, response)
    }
}
