package org.gameyfin.app.api.security

import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isAdmin
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Component
import java.security.Principal

class ApiPrincipal(
    val credentialId: String,
    val credentialName: String,
    val kind: CredentialKind,
    val userId: Long?,
    private val actorName: String
) : Principal {
    override fun getName(): String = actorName
    override fun toString(): String = "ApiPrincipal($credentialId)"
}

class ApiAuthentication(val actor: ApiPrincipal, authorities: Collection<GrantedAuthority>) : AbstractAuthenticationToken(authorities) {
    init { isAuthenticated = true }
    override fun getPrincipal(): ApiPrincipal = actor
    override fun getCredentials(): Any? = null
    override fun getName(): String = actor.name
}

@Component
class ApiAccess {
    fun principal(): ApiPrincipal = (getCurrentAuth() as? ApiAuthentication)?.actor
        ?: throw AccessDeniedException("API authentication required")

    fun require(scope: String, admin: Boolean = false): ApiPrincipal {
        check(scope in ApiScopes.all) { "Unknown API permission" }
        val actor = principal()
        val auth = getCurrentAuth()!!
        val allowed = if (actor.kind == CredentialKind.SERVICE) {
            auth.authorities.any { it.authority == "SCOPE_$scope" }
        } else if (admin) auth.isAdmin() else true
        if (!allowed) throw AccessDeniedException("Missing permission: $scope")
        return actor
    }

    fun personal(): ApiPrincipal = principal().also {
        if (it.kind != CredentialKind.PERSONAL) throw AccessDeniedException("A personal access token is required")
    }
}
