package org.gameyfin.app.collections.entities

import jakarta.persistence.*
import org.gameyfin.app.games.entities.Game
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
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

    @Lob
    var description: String? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    var games: MutableSet<Game> = mutableSetOf(),

    @Embedded
    var metadata: CollectionMetadata = CollectionMetadata()
) {
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
