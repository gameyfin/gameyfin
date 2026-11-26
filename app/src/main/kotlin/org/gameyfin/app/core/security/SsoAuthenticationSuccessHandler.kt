package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.config.MatchUsersBy
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.RoleService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.parseEmailAddress
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyAuthoritiesMapper
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class SsoAuthenticationSuccessHandler(
    private val userService: UserService,
    private val roleService: RoleService,
    private val config: ConfigService,
    roleHierarchy: RoleHierarchy,
    private val avatarSource: OidcAvatarSource,
) : AuthenticationSuccessHandler {

    private val authoritiesMapper = RoleHierarchyAuthoritiesMapper(roleHierarchy)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oidcUser = authentication.principal as OidcUser

        // Resolve the username using the configured claim with automatic fallback
        val usernameAttributeName = config.get(ConfigProperties.SSO.OIDC.UsernameClaim) ?: "preferred_username"
        val resolvedUsername = oidcUser.resolvedUsername(usernameAttributeName)

        // Check if user is already registered via SSO
        val subject = oidcUser.subject ?: error("OIDC user has no subject claim")
        var matchedUser = userService.findByOidcProviderId(subject)
        val email = try {
            parseEmailAddress(oidcUser.email)
        } catch (_: IllegalArgumentException) {
            rejectSignIn(request, response, "Invalid OIDC email claim")
            return
        }
        if (matchedUser == null && email == null && userService.emailRequired) {
            rejectSignIn(request, response, "OIDC user has no email claim")
            return
        }

        // If user is not registered via SSO, check if user is already registered by username or email
        // This is meant to map existing users to SSO users
        if (matchedUser == null) {
            matchedUser = when (config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy)) {
                MatchUsersBy.username -> userService.getByUsername(resolvedUsername)
                MatchUsersBy.email -> email?.let(userService::getByEmail)

                else -> error("Unknown 'match users by' configuration")
            }
        }

        if (matchedUser?.oidcProviderId?.let { it != subject } == true) {
            rejectSignIn(request, response, "Account is already linked to an OIDC identity")
            return
        }

        val grantedAuthorities = roleService.extractGrantedAuthorities(oidcUser.authorities)
        val profile = User(oidcUser, resolvedUsername, email).apply {
            id = matchedUser?.id
            roles = roleService.authoritiesToRoles(grantedAuthorities).ifEmpty { listOf(Role.USER) }
        }
        userService.registerOrUpdateOidcUser(profile, avatarSource.load(oidcUser))

        // Update SecurityContext with expanded authorities through RoleHierarchy
        val mappedAuthorities = authoritiesMapper.mapAuthorities(grantedAuthorities)

        val authPrincipal = authentication.principal
        val authCredentials = authentication.credentials

        if (authPrincipal != null && authCredentials != null) {
            val newAuth = UsernamePasswordAuthenticationToken(authPrincipal, authCredentials, mappedAuthorities)
            SecurityContextHolder.getContext().authentication = newAuth
        }

        // Share the onboarding and validated-destination decision with local login.
        response.sendRedirect("/login/success")
        return
    }

    private fun rejectSignIn(request: HttpServletRequest, response: HttpServletResponse, message: String) {
        SecurityContextHolder.clearContext()
        request.getSession(false)?.invalidate()
        response.sendError(HttpServletResponse.SC_FORBIDDEN, message)
    }
}
