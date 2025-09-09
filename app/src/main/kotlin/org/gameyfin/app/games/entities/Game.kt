package org.gameyfin.app.games.entities

import jakarta.persistence.*
import jakarta.persistence.CascadeType.*
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.net.URI
import java.nio.file.Path
import java.time.Instant

@Entity
@EntityListeners(GameEntityListener::class)
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    val library: Library,

    var title: String? = null,

    @ManyToOne(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var coverImage: Image? = null,

    @ManyToOne(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var headerImage: Image? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    var comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    var summary: String? = null,

    var release: Instant? = null,

    var userRating: Int? = null,

    var criticRating: Int? = null,

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var publishers: MutableList<Company> = mutableListOf(),

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var developers: MutableList<Company> = mutableListOf(),

    @ElementCollection(targetClass = Genre::class)
    var genres: List<Genre> = emptyList(),

    @ElementCollection(targetClass = Theme::class)
    var themes: List<Theme> = emptyList(),

    @ElementCollection
    var keywords: List<String> = emptyList(),

    @ElementCollection(targetClass = GameFeature::class)
    var features: List<GameFeature> = emptyList(),

    @ElementCollection(targetClass = PlayerPerspective::class)
    var perspectives: List<PlayerPerspective> = emptyList(),

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var images: MutableList<Image> = mutableListOf(),

    @ElementCollection
    var videoUrls: List<URI> = emptyList(),

    @Embedded
    var metadata: GameMetadata
) {
    constructor(path: Path, library: Library) : this(library = library, metadata = GameMetadata(path = path.toString()))
}