package org.gameyfin.app.requests

import jakarta.persistence.*
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

typealias ExternalProviderIds = Map<String, String>

@Entity
class GameRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val release: Instant,

    @Column(nullable = false)
    var status: GameRequestStatus,

    @ManyToOne(fetch = FetchType.EAGER)
    var requester: User? = null,

    var voters: MutableList<User> = mutableListOf(),

    @ElementCollection
    val externalProviderIds: ExternalProviderIds
)