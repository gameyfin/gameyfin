package org.gameyfin.app.requests.entities

import jakarta.persistence.*
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

typealias ExternalProviderIds = Map<String, String>

@Entity
@EntityListeners(GameRequestEntityListener::class, AuditingEntityListener::class)
class GameRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val release: Instant,

    @ElementCollection
    val externalProviderIds: ExternalProviderIds,

    @Column(nullable = false)
    var status: GameRequestStatus,

    @ManyToOne(fetch = FetchType.EAGER)
    var requester: User? = null,

    @OneToMany
    var voters: MutableList<User> = mutableListOf(),

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null
)