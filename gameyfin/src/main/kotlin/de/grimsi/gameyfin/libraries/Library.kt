package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game
import jakarta.persistence.*

@Entity
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,

    val name: String,

    @ElementCollection(fetch = FetchType.EAGER)
    val directories: Set<String>,

    @ManyToMany(cascade = [CascadeType.ALL])
    val games: MutableSet<Game> = mutableSetOf()
)