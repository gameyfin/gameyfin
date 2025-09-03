package org.gameyfin.app.users

import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.stereotype.Service

@Service
class RoleService(
    private val userRepository: UserRepository,
    private val configService: ConfigService
) {

    companion object {
        const val SSO_ROLE_PREFIX = "GAMEYFIN_"
        const val INTERNAL_ROLE_PREFIX = "ROLE_"
    }

    fun getAllRoles(): List<Role> {
        return Role.entries
    }

    /**
     * @return the number of registered users with a given role
     */
    fun getUserCountForRole(role: Role): Int {
        return userRepository.countUserByRolesContains(role)
    }

    fun getHighestRole(roles: Collection<Role>): Role {
        return roles.maxByOrNull { it.powerLevel } ?: Role.USER
    }

    fun getHighestRoleFromAuthorities(authorities: Collection<GrantedAuthority>): Role {
        return getHighestRole(authoritiesToRoles(authorities))
    }

    fun getRolesBelowUser(user: User): List<Role> {
        val highestUserRole = getHighestRole(user.roles)
        return Role.entries.filter { it.powerLevel < highestUserRole.powerLevel }
    }

    fun getRolesBelowAuth(auth: Authentication): List<Role> {
        val highestUserRole = getHighestRole(auth.authorities.mapNotNull { Role.Companion.safeValueOf(it.authority) })
        return Role.entries.filter { it.powerLevel < highestUserRole.powerLevel }
    }

    fun authoritiesToRoles(authorities: Collection<GrantedAuthority>): List<Role> {
        return authorities.mapNotNull { Role.Companion.safeValueOf(it.authority) }
    }

    /**
     * Extracts granted authorities from a collection of granted authorities.
     * Also converts SSO roles to internal roles.
     * SSO roles are assumed to be prefixed with "GAMEYFIN_" to avoid collision with other SSO apps managed by the same provider.
     * Internal roles are prefixed with "ROLE_" as per Spring Security conventions.
     * Ignore any authorities that do not start with "GAMEYFIN_".
     *
     * @return filtered and mapped collection of granted authorities
     */
    fun extractGrantedAuthorities(authorities: Collection<GrantedAuthority>): Collection<SimpleGrantedAuthority> {
        val mappedAuthorities = authorities.asSequence()
            .filterIsInstance<OidcUserAuthority>()
            .flatMap { oidcUserAuthority ->
                val userInfo = oidcUserAuthority.userInfo
                val rolesClaim = configService.get(ConfigProperties.SSO.OIDC.RolesClaim)
                val roles = userInfo.getClaim<List<String>>(rolesClaim) ?: return@flatMap emptySequence()
                roles.asSequence().mapNotNull {
                    if (it.startsWith(SSO_ROLE_PREFIX)) SimpleGrantedAuthority(
                        it.replace(SSO_ROLE_PREFIX, INTERNAL_ROLE_PREFIX)
                    )
                    else null
                }
            }
            .toSet()

        // Add USER role if no roles are present
        if (mappedAuthorities.isEmpty()) {
            mappedAuthorities.plus(SimpleGrantedAuthority(Role.Names.USER))
        }

        return mappedAuthorities
    }
}