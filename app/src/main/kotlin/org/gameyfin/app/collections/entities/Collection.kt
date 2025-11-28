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

    @Column
    var description: String? = null,

    @ManyToMany(fetch = FetchType.EAGER)
    var games: MutableSet<Game> = mutableSetOf()
) {
    fun addGame(game: Game) {
        games.add(game)
        if (!game.collections.contains(this)) {
            game.collections.add(this)
        }
    }

    fun removeGame(game: Game) {
        games.remove(game)
        game.collections.remove(this)
    }
}
