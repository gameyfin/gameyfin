package de.grimsi.gameyfin.games

import jakarta.persistence.*
import java.time.Instant

@Entity
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    val title: String,

    @Lob
    @Column(columnDefinition = "CLOB")
    val comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    val summary: String,

    val release: Instant,

    @ElementCollection
    val publishers: List<String>,

    @ElementCollection
    val developers: List<String>,

    @Column(unique = true)
    val path: String,

    val source: String
)