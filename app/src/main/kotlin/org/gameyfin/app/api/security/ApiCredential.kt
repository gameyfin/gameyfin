package org.gameyfin.app.api.security

import jakarta.persistence.*
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.*

enum class CredentialKind { PERSONAL, SERVICE }

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "api_credential")
class ApiCredential(
    @Id var id: String = UUID.randomUUID().toString(),
    @Enumerated(EnumType.STRING) @Column(nullable = false) var kind: CredentialKind,
    @Column(nullable = false, length = 100) var name: String,
    @Column(nullable = false, length = 64) var secretDigest: String,
    @ManyToOne(fetch = FetchType.LAZY) @OnDelete(action = OnDeleteAction.CASCADE) var owner: User? = null,
    @ManyToOne(fetch = FetchType.LAZY) @OnDelete(action = OnDeleteAction.SET_NULL) var creator: User? = null,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_credential_scope", joinColumns = [JoinColumn(name = "credential_id")])
    @Column(name = "scope", nullable = false)
    var scopes: MutableSet<String> = mutableSetOf(),
    @Column(nullable = false) var createdAt: Instant = Instant.now(),
    var expiresAt: Instant? = null,
    var revokedAt: Instant? = null,
    var lastUsedAt: Instant? = null
)

interface ApiCredentialRepository : JpaRepository<ApiCredential, String> {
    fun findAllByOwnerIdOrderByCreatedAtDesc(ownerId: Long): List<ApiCredential>
    fun findAllByKindOrderByCreatedAtDesc(kind: CredentialKind): List<ApiCredential>
}

/**
 * Permissions grantable to service credentials. Personal tokens implicitly hold every scope
 * their owner may use: all scopes for administrators, only non-[adminOnly] scopes otherwise.
 */
enum class ApiScope(val value: String, val adminOnly: Boolean = false) {
    GAMES_READ("games:read"),
    GAMES_WRITE("games:write", adminOnly = true),
    GAMES_DELETE("games:delete", adminOnly = true),
    LIBRARIES_READ("libraries:read"),
    LIBRARIES_WRITE("libraries:write", adminOnly = true),
    LIBRARIES_DELETE("libraries:delete", adminOnly = true),
    LIBRARIES_SCAN("libraries:scan", adminOnly = true),
    COLLECTIONS_READ("collections:read"),
    COLLECTIONS_WRITE("collections:write", adminOnly = true),
    COLLECTIONS_DELETE("collections:delete", adminOnly = true),
    DOWNLOADS_READ("downloads:read"),
    REQUESTS_READ("requests:read"),
    REQUESTS_WRITE("requests:write", adminOnly = true),
    REQUESTS_DELETE("requests:delete"),
    TASKS_READ("tasks:read", adminOnly = true),
    INSTANCE_READ("instance:read"),
    USERS_READ("users:read");

    val authority: String get() = AUTHORITY_PREFIX + value

    override fun toString() = value

    companion object {
        const val AUTHORITY_PREFIX = "SCOPE_"
        val all: Set<String> = entries.map { it.value }.toSet()
        val user: Set<String> = entries.filterNot { it.adminOnly }.map { it.value }.toSet()
    }
}
