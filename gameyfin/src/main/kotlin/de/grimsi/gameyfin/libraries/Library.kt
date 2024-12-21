package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game
import jakarta.persistence.*

@Entity
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,

    val name: String,

    @Column(unique = true)
    val path: String,

    @OneToMany
    val games: Set<Game> = emptySet()
)