package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.persistence.RoleRepository
import jakarta.transaction.Transactional
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority
import org.springframework.stereotype.Service

@Service
@Transactional
class RoleService(
    private val roleRepository: RoleRepository
) {

    companion object {
        const val SSO_ROLE_PREFIX = "GAMEYFIN_"
        const val INTERNAL_ROLE_PREFIX = "ROLE_"
    }

    /**
     * @return the number of registered users with a given role
     * @return 0 if a role does not exist
     */
    fun getUserCountForRole(role: Roles): Int {
        val r = roleRepository.findByRolename(role.roleName) ?: return 0
        return r.users.size
    }

    fun toRoles(roles: Collection<Roles>): Set<Role> {
        return roles.mapNotNull { r -> roleRepository.findByRolename(r.roleName) }.toSet()
    }

    fun toRole(role: Roles): Role {
        return roleRepository.findByRolename(role.roleName)
            ?: throw RuntimeException("Role ${role.roleName} does not exist")
    }

    fun authoritiesToRoles(authorities: Collection<GrantedAuthority>): Set<Role> {
        return authorities.mapNotNull { a -> roleRepository.findByRolename(a.authority) }.toSet()
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
                val roles = userInfo.getClaim<List<String>>("roles")
                roles.asSequence().mapNotNull {
                    if (it.startsWith(SSO_ROLE_PREFIX)) SimpleGrantedAuthority(
                        it.replace(
                            SSO_ROLE_PREFIX,
                            INTERNAL_ROLE_PREFIX
                        )
                    )
                    else null
                }
            }
            .toSet()

        if (mappedAuthorities.isEmpty()) {
            mappedAuthorities.plus(SimpleGrantedAuthority(Roles.Names.USER))
        }

        return mappedAuthorities
    }
}