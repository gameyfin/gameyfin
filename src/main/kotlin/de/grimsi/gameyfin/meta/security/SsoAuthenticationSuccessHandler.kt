package de.grimsi.gameyfin.meta.security

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.config.MatchUsersBy
import de.grimsi.gameyfin.users.RoleService
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.entities.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class SsoAuthenticationSuccessHandler(
    private val userService: UserService,
    private val roleService: RoleService,
    private val config: ConfigService
) : AuthenticationSuccessHandler {

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
            matchedUser = when (config.get(ConfigProperties.SsoMatchExistingUsersBy)) {
                MatchUsersBy.USERNAME -> userService.getByUsername(oidcUser.preferredUsername)
                MatchUsersBy.EMAIL -> userService.getByEmail(oidcUser.email)
                else -> throw IllegalStateException("Unknown 'match users by' configuration")
            }
        }

        // User could not be found in the database
        if (matchedUser == null) {
            // Check if new user registration is enabled
            if (config.get(ConfigProperties.SsoAutoRegisterNewUsers) == false) {
                response.sendRedirect("/")
                return
            }

            // Register as new user
            matchedUser = User(oidcUser)
        } else {
            // Update user with new SSO data
            matchedUser.username = oidcUser.preferredUsername
            matchedUser.email = oidcUser.email
            matchedUser.email_confirmed = true
            matchedUser.oidcProviderId = oidcUser.subject
        }


        val grantedAuthorities = roleService.extractGrantedAuthorities(oidcUser.authorities)
        matchedUser.roles = roleService.authoritiesToRoles(grantedAuthorities)
        userService.registerOrUpdateUser(matchedUser)

        response.sendRedirect("/")
        return
    }
}