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

object ApiScopes {
    val all = setOf(
        "games:read",
        "games:write",
        "games:delete",
        "libraries:read",
        "libraries:write",
        "libraries:delete",
        "libraries:scan",
        "collections:read",
        "collections:write",
        "collections:delete",
        "downloads:read",
        "requests:read",
        "requests:write",
        "requests:delete",
        "tasks:read",
        "instance:read",
        "users:read"
    )
    val user = setOf(
        "games:read",
        "libraries:read",
        "collections:read",
        "downloads:read",
        "requests:read",
        "requests:write",
        "requests:delete",
        "instance:read",
        "users:read"
    )
}
