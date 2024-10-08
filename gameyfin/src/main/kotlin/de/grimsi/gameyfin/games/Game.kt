package de.grimsi.gameyfin.games

import jakarta.persistence.*
import java.nio.file.Path

@Entity
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    val id: Long? = null,

    val title: String,

    @Lob
    @Column(columnDefinition = "CLOB")
    val comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    val summary: String,

    @Column(unique = true)
    val path: String
)