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
    private val roleHierarchy: RoleHierarchy,
) : AuthenticationSuccessHandler {

    private val authoritiesMapper = RoleHierarchyAuthoritiesMapper(roleHierarchy)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val oidcUser = authentication.principal as OidcUser

        // Check if user is already registered via SSO
        var matchedUser = userService.findByOidcProviderId(oidcUser.subject)

        // If user is not registered via SSO, check if user is already registered by username or email
        // This is meant to map existing users to SSO users
        if (matchedUser == null) {
            matchedUser = when (config.get(ConfigProperties.SSO.OIDC.MatchExistingUsersBy)) {
                MatchUsersBy.username -> userService.getByUsername(oidcUser.preferredUsername)
                MatchUsersBy.email -> userService.getByEmail(oidcUser.email)
                else -> throw IllegalStateException("Unknown 'match users by' configuration")
            }
        }

        // User could not be found in the database
        if (matchedUser == null) {
            // TODO: User registration is currently forced, but this should be configurable.
            //  However, this causes conflict with user preferences and game entities (since both reference the user entity)
            // Check if new user registration is enabled
            //if (config.get(ConfigProperties.SSO.OIDC.AutoRegisterNewUsers) == false) {
            //    response.sendRedirect("/")
            //    return
            //

            // Register as new user
            matchedUser = User(oidcUser)
        } else {
            // Update user with new SSO data
            matchedUser.username = oidcUser.preferredUsername
            matchedUser.email = oidcUser.email
            matchedUser.emailConfirmed = true
            matchedUser.oidcProviderId = oidcUser.subject
        }


        val grantedAuthorities = roleService.extractGrantedAuthorities(oidcUser.authorities)
        val roles = roleService.authoritiesToRoles(grantedAuthorities).ifEmpty { listOf(Role.USER) }
        matchedUser.roles = roles
        userService.registerOrUpdateUser(matchedUser)

        // Update SecurityContext with expanded authorities through RoleHierarchy
        val mappedAuthorities = authoritiesMapper.mapAuthorities(grantedAuthorities)

        val newAuth =
            UsernamePasswordAuthenticationToken(authentication.principal, authentication.credentials, mappedAuthorities)
        SecurityContextHolder.getContext().authentication = newAuth

        response.sendRedirect("/")
        return
    }
}