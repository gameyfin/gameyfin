package org.gameyfin.app.collections.entities

import jakarta.persistence.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.Formula
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@EntityListeners(CollectionEntityListener::class)
class Collection(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null,

    @Column(nullable = false, unique = true)
    var name: String,

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    var description: String? = null,

    @ManyToMany(fetch = FetchType.LAZY)
    var games: MutableSet<Game> = mutableSetOf(),

    @Embedded
    var metadata: CollectionMetadata = CollectionMetadata(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: CollectionKind = CollectionKind.CURATED,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var visibility: CollectionVisibility = CollectionVisibility.PUBLIC,

    @ManyToOne(fetch = FetchType.LAZY)
    var owner: User? = null,

    // Member labels can repeat without changing the legacy unique name constraint.
    var memberName: String? = null,

    var favouritesOwnerId: Long? = null
) {
    @Formula("coalesce(member_name, name)")
    private var displaySortName: String? = null

    val displayName: String get() = memberName ?: name

    fun addGame(game: Game) {
        games.add(game)
        if (!game.collections.contains(this)) {
            game.collections.add(this)
        }
        // Track when the game was added
        game.id?.let { gameId ->
            metadata.gamesAddedAt[gameId] = Instant.now()
        }
        // Force update to trigger @PostUpdate callback
        updatedAt = Instant.now()
    }

    fun removeGame(game: Game) {
        games.remove(game)
        game.collections.remove(this)
        // Remove the timestamp tracking for this game
        game.id?.let { gameId ->
            metadata.gamesAddedAt.remove(gameId)
        }
        // Force update to trigger @PostUpdate callback
        updatedAt = Instant.now()
    }
}

enum class CollectionKind { CURATED, CUSTOM, FAVOURITES }

enum class CollectionVisibility { PRIVATE, MEMBERS_ONLY, PUBLIC }
