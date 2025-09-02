package org.gameyfin.app.requests.entities

import jakarta.persistence.*
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@Entity
@EntityListeners(GameRequestEntityListener::class, AuditingEntityListener::class)
class GameRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(nullable = false)
    val title: String,

    @Column(nullable = false)
    val release: Instant?,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: GameRequestStatus,

    @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    var requester: User,

    @OneToMany(fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    var voters: MutableList<User> = mutableListOf(),

    var linkedGameId: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null
)