package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game
import jakarta.persistence.*

@Entity
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    var name: String,

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = [CascadeType.ALL])
    var directories: MutableList<DirectoryMapping> = ArrayList(),

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true)
    var games: MutableList<Game> = ArrayList(),

    @ElementCollection(fetch = FetchType.EAGER)
    var unmatchedPaths: MutableList<String> = ArrayList()
)