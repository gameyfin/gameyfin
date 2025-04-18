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

    @ManyToMany(fetch = FetchType.EAGER)
    var games: MutableSet<Game> = HashSet<Game>()
)