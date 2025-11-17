package org.gameyfin.app.requests.entities

import jakarta.persistence.*
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@EntityListeners(GameRequestEntityListener::class)
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
    val platform: Platform,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var status: GameRequestStatus,

    @ManyToOne(fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    var requester: User? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var voters: MutableSet<User> = mutableSetOf(),

    var linkedGameId: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null
)