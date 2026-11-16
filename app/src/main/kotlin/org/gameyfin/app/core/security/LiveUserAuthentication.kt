package org.gameyfin.app.core.security

import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserEvent
import org.gameyfin.app.users.extensions.toAuthorities
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue

/** The HTTP session and Hilla's captured websocket principal share this live identity. */
class LiveUserAuthentication(
    private val original: Authentication,
    user: ExtendedUserInfoDto,
    private val hierarchy: RoleHierarchy
) : Authentication {
    val userId = user.id
    val sessionPrincipal: Any? get() = original.principal

    @Volatile
    private var current: ExtendedUserInfoDto? = user

    @Volatile
    private var revoked = !user.enabled

    init {
        instances.add(WeakReference(this))
    }

    fun refresh(user: ExtendedUserInfoDto?) {
        if (user?.enabled != true) revoked = true
        current = user
    }

    override fun getName(): String = current?.username ?: original.name
    override fun getAuthorities(): Collection<GrantedAuthority> = current
        ?.takeIf { it.enabled && !revoked }
        ?.let {
            hierarchy.getReachableGrantedAuthorities(it.roles.toAuthorities()) +
                    original.authorities.filter { authority ->
                        !authority.authority.orEmpty().startsWith("ROLE_") &&
                                !authority.authority.orEmpty().startsWith("GAMEYFIN_")
                    }
        } ?: emptyList()

    override fun isAuthenticated(): Boolean = original.isAuthenticated && !revoked && current?.enabled == true
    override fun setAuthenticated(authenticated: Boolean) {
        original.isAuthenticated = authenticated
    }

    override fun getCredentials(): Any? = original.credentials
    override fun getDetails(): Any? = original.details
    override fun getPrincipal(): Any? {
        val principal = original.principal
        // Keep OIDC claims/tokens intact; local principals follow username changes.
        return if (principal is UserDetails) User(name, principal.password ?: "", authorities) else principal
    }

    companion object {
        private val instances = ConcurrentLinkedQueue<WeakReference<LiveUserAuthentication>>()

        fun update(event: UserEvent) {
            instances.removeIf { reference ->
                val auth = reference.get()
                if (auth?.userId == event.id) auth.refresh(event.user)
                auth == null
            }
        }
    }
}
