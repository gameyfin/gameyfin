package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game
import jakarta.persistence.*

@Entity
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    var name: String,

    @ElementCollection(fetch = FetchType.EAGER)
    var directories: MutableSet<String> = HashSet<String>(),

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    var games: MutableSet<Game> = HashSet<Game>(),

    @ElementCollection(fetch = FetchType.EAGER)
    var unmatchedPaths: MutableSet<String> = HashSet<String>()
)