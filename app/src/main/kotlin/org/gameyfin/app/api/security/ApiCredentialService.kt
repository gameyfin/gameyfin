package org.gameyfin.app.api.security

import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isAdmin
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.*

data class CredentialView(
    val id: String, val kind: CredentialKind, val name: String, val prefix: String,
    val scopes: Set<String>, val createdAt: Instant, val expiresAt: Instant?,
    val revokedAt: Instant?, val lastUsedAt: Instant?, val status: String
)

class CreatedCredential(val credential: CredentialView, val secret: String) {
    override fun toString() = "CreatedCredential(${credential.id})"
}

data class CredentialInput(val name: String, val expiresAt: Instant? = null, val scopes: Set<String> = emptySet())

@Service
class ApiCredentialService(
    private val repository: ApiCredentialRepository,
    private val users: UserRepository,
    private val userService: UserService,
    private val hierarchy: RoleHierarchy
) {
    companion object {
        private val random = SecureRandom()
        fun digest(secret: String): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)))

        fun prefix(kind: CredentialKind) = if (kind == CredentialKind.PERSONAL) "gf_pat_" else "gf_key_"
    }

    @Transactional
    fun authenticate(token: String): ApiAuthentication {
        fun invalid(): Nothing = throw BadCredentialsException("Invalid API key")
        if (token.length !in 80..100) invalid()
        val parts = token.split('.', limit = 2)
        if (parts.size != 2 || parts[0].length != 43 || parts[1].length != 43) invalid()
        val key = repository.findById(parts[0].substring(7)).orElse(null) ?: invalid()
        if (parts[0] != prefix(key.kind) + key.id || !MessageDigest.isEqual(
                digest(parts[1]).toByteArray(Charsets.US_ASCII), key.secretDigest.toByteArray(Charsets.US_ASCII)
            )
        ) invalid()
        val now = Instant.now()
        if (key.revokedAt != null || key.expiresAt?.let { !now.isBefore(it) } == true) invalid()
        val owner = key.owner
        if (key.kind == CredentialKind.PERSONAL && (owner == null || !owner.enabled)) invalid()
        val authorities = if (key.kind == CredentialKind.PERSONAL) {
            hierarchy.getReachableGrantedAuthorities(owner!!.roles.map { SimpleGrantedAuthority(it.roleName) })
        } else key.scopes.map { SimpleGrantedAuthority(ApiScope.AUTHORITY_PREFIX + it) }
        if (key.lastUsedAt == null || key.lastUsedAt!!.isBefore(now.minusSeconds(60))) key.lastUsedAt = now
        return ApiAuthentication(
            ApiPrincipal(
                key.id, key.name, key.kind, owner?.id,
                owner?.username ?: "api-key:${key.id}"
            ), authorities
        )
    }

    private fun manager(service: Boolean): User {
        val auth = getCurrentAuth() ?: throw AccessDeniedException("Sign in required")
        if (!auth.isAuthenticated || auth is ApiAuthentication || (service && !auth.isAdmin())) {
            throw AccessDeniedException("An authorized browser session is required")
        }
        val id = userService.getUserInfo().id
        return users.findById(id).orElseThrow { AccessDeniedException("Account not found") }.also {
            if (!it.enabled) throw AccessDeniedException("Account disabled")
        }
    }

    @Transactional(readOnly = true)
    fun list(service: Boolean): List<CredentialView> {
        val user = manager(service)
        return (if (service) repository.findAllByKindOrderByCreatedAtDesc(CredentialKind.SERVICE)
        else repository.findAllByOwnerIdOrderByCreatedAtDesc(user.id!!)).map(::view)
    }

    @Transactional
    fun create(service: Boolean, input: CredentialInput): CreatedCredential {
        val user = manager(service)
        validate(input, service)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        val key = repository.save(
            ApiCredential(
                kind = if (service) CredentialKind.SERVICE else CredentialKind.PERSONAL,
                name = input.name.trim(), secretDigest = digest(secret), owner = if (service) null else user,
                creator = user, scopes = input.scopes.toMutableSet(), expiresAt = input.expiresAt
            )
        )
        return CreatedCredential(view(key), prefix(key.kind) + key.id + "." + secret)
    }

    @Transactional
    fun update(id: String, service: Boolean, input: CredentialInput): CredentialView {
        val key = managed(id, service)
        require(key.revokedAt == null) { "Revoked credentials cannot be edited" }
        validate(input, service)
        key.name = input.name.trim()
        key.expiresAt = input.expiresAt
        key.scopes = input.scopes.toMutableSet()
        return view(key)
    }

    @Transactional
    fun revoke(id: String, service: Boolean) {
        val key = managed(id, service)
        if (key.revokedAt == null) key.revokedAt = Instant.now()
    }

    @Transactional
    fun delete(id: String, service: Boolean) {
        val key = managed(id, service)
        require(key.revokedAt != null) { "Only revoked credentials can be deleted" }
        repository.delete(key)
    }

    @Transactional
    fun regenerate(id: String, service: Boolean): CreatedCredential {
        val key = managed(id, service)
        require(key.revokedAt == null) { "Revoked credentials cannot be regenerated" }
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        key.secretDigest = digest(secret)
        return CreatedCredential(view(key), prefix(key.kind) + key.id + "." + secret)
    }

    private fun managed(id: String, service: Boolean): ApiCredential {
        val user = manager(service)
        val key = repository.findById(id).orElseThrow { NoSuchElementException("Credential not found") }
        if (service && key.kind != CredentialKind.SERVICE || !service && (key.kind != CredentialKind.PERSONAL || key.owner?.id != user.id)) {
            throw AccessDeniedException("Credential access denied")
        }
        return key
    }

    private fun validate(input: CredentialInput, service: Boolean) {
        require(input.name.trim().length in 1..100) { "Name must contain 1 to 100 characters" }
        require(input.expiresAt == null || input.expiresAt.isAfter(Instant.now())) { "Expiry must be in the future" }
        require(if (service) input.scopes.isNotEmpty() && ApiScope.all.containsAll(input.scopes) else input.scopes.isEmpty()) { "Invalid permissions" }
    }

    private fun view(key: ApiCredential) = CredentialView(
        key.id,
        key.kind,
        key.name,
        prefix(key.kind) + key.id.take(8),
        key.scopes.toSet(),
        key.createdAt,
        key.expiresAt,
        key.revokedAt,
        key.lastUsedAt,
        when {
            key.revokedAt != null -> "REVOKED"; key.expiresAt?.let {
            !Instant.now().isBefore(it)
        } == true -> "EXPIRED"; else -> "ACTIVE"
        })
}
