package org.gameyfin.app.games.entities

import jakarta.persistence.*
import jakarta.persistence.CascadeType.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.pluginapi.gamemetadata.*
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

    @ElementCollection(targetClass = Platform::class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var platforms: MutableList<Platform> = mutableListOf(),

    var title: String? = null,

    @ManyToOne(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var coverImage: Image? = null,

    @ManyToOne(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var headerImage: Image? = null,

    @Lob
    var comment: String? = null,

    @Lob
    var summary: String? = null,

    var release: Instant? = null,

    var userRating: Int? = null,

    var criticRating: Int? = null,

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var publishers: MutableList<Company> = mutableListOf(),

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var developers: MutableList<Company> = mutableListOf(),

    @ElementCollection(targetClass = Genre::class, fetch = FetchType.EAGER)
    var genres: List<Genre> = emptyList(),

    @ElementCollection(targetClass = Theme::class, fetch = FetchType.EAGER)
    var themes: List<Theme> = emptyList(),

    @ElementCollection(fetch = FetchType.EAGER)
    var keywords: List<String> = emptyList(),

    @ElementCollection(targetClass = GameFeature::class, fetch = FetchType.EAGER)
    var features: List<GameFeature> = emptyList(),

    @ElementCollection(targetClass = PlayerPerspective::class, fetch = FetchType.EAGER)
    var perspectives: List<PlayerPerspective> = emptyList(),

    @ManyToMany(cascade = [PERSIST, MERGE, REFRESH], fetch = FetchType.EAGER)
    var images: MutableList<Image> = mutableListOf(),

    @ElementCollection(fetch = FetchType.EAGER)
    var videoUrls: List<URI> = emptyList(),

    @ManyToMany(mappedBy = "games", fetch = FetchType.EAGER)
    var collections: MutableList<Collection> = mutableListOf(),

    @Embedded
    var metadata: GameMetadata
) {
    constructor(path: Path, library: Library) : this(library = library, metadata = GameMetadata(path = path.toString()))

    /**
     * Return the percentage of fields with non-empty content (except "comment", "collections" and "metadata").
     * 
     * @return percentage of filled fields as an integer between 0 and 100
     */
    fun calculateCompletenessScore(): Int {
        // Hint: When a field gets added/removed, a DB migration script needs to re-calculate the scores!
        val fields: List<Boolean> = listOf(
            !title.isNullOrBlank(),
            coverImage != null,
            headerImage != null,
            !summary.isNullOrBlank(),
            release != null,
            userRating != null,
            criticRating != null,
            platforms.isNotEmpty(),
            publishers.isNotEmpty(),
            developers.isNotEmpty(),
            genres.isNotEmpty(),
            themes.isNotEmpty(),
            keywords.isNotEmpty(),
            features.isNotEmpty(),
            perspectives.isNotEmpty(),
            images.isNotEmpty(),
            videoUrls.isNotEmpty(),
        )
        val filled = fields.count { it }
        return (filled * 100) / fields.size
    }
}