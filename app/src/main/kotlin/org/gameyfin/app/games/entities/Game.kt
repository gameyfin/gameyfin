package org.gameyfin.app.games.entities

import jakarta.persistence.*
import jakarta.persistence.CascadeType.MERGE
import jakarta.persistence.CascadeType.REFRESH
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.pluginapi.gamemetadata.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
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

    @ManyToOne(cascade = [MERGE, REFRESH], fetch = FetchType.EAGER)
    var coverImage: Image? = null,

    @ManyToOne(cascade = [MERGE, REFRESH], fetch = FetchType.EAGER)
    var headerImage: Image? = null,

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    var comment: String? = null,

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    var summary: String? = null,

    var release: Instant? = null,

    var userRating: Int? = null,

    var criticRating: Int? = null,

    @ManyToMany(cascade = [MERGE, REFRESH], fetch = FetchType.EAGER)
    var publishers: MutableList<Company> = mutableListOf(),

    @ManyToMany(cascade = [MERGE, REFRESH], fetch = FetchType.EAGER)
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

    images: MutableList<Image> = mutableListOf(),
    videoUrls: List<URI> = emptyList(),
    mediaOrder: List<String> = emptyList(),

    @ManyToMany(mappedBy = "games", fetch = FetchType.LAZY)
    var collections: MutableList<Collection> = mutableListOf(),

    @Embedded
    var metadata: GameMetadata
) {
    /** The gallery has one persisted order, shared by screenshots and videos. */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "game_media", joinColumns = [JoinColumn(name = "game_id")])
    @OrderColumn(name = "media_position")
    var media: MutableList<GameMedia> = (images.map { GameMedia(image = it) } +
            videoUrls.map { GameMedia(videoUrl = it) }).toMutableList()

    // Keep the existing API and metadata-provider views backed by the shared gallery.
    var images: MutableList<Image>
        get() = object : AbstractMutableList<Image>() {
            private fun positions() = media.indices.filter { media[it].image != null }
            override val size: Int get() = positions().size
            override fun get(index: Int): Image = media[positions()[index]].image!!
            override fun set(index: Int, element: Image): Image {
                val position = positions()[index]
                return media.set(position, GameMedia(image = element)).image!!
            }

            override fun add(index: Int, element: Image) {
                val positions = positions()
                require(index in 0..positions.size)
                val position = if (index < positions.size) positions[index] else
                    positions.lastOrNull()?.plus(1) ?: media.size
                media.add(position, GameMedia(image = element))
            }

            override fun removeAt(index: Int): Image = media.removeAt(positions()[index]).image!!
        }
        set(value) = replaceMedia(true, value.map { GameMedia(image = it) })

    var videoUrls: List<URI>
        get() = media.mapNotNull { it.videoUrl }
        set(value) = replaceMedia(false, value.map { GameMedia(videoUrl = it) })

    var mediaOrder: List<String>
        get() = media.map { if (it.image != null) "image" else "video" }
        set(value) {
            val images = media.filter { it.image != null }.iterator()
            val videos = media.filter { it.videoUrl != null }.iterator()
            val ordered = buildList {
                value.forEach { kind ->
                    val items = when (kind) {
                        "image" -> images
                        "video" -> videos
                        else -> error("Unknown media kind: $kind")
                    }
                    if (items.hasNext()) add(items.next())
                }
                addAll(images.asSequence())
                addAll(videos.asSequence())
            }
            media.clear()
            media.addAll(ordered)
        }

    init {
        if (mediaOrder.isNotEmpty()) this.mediaOrder = mediaOrder
    }

    private fun replaceMedia(images: Boolean, replacements: List<GameMedia>) {
        val remaining = replacements.iterator()
        val updated = buildList {
            media.forEach { item ->
                if ((item.image != null) != images) add(item)
                else if (remaining.hasNext()) add(remaining.next())
            }
            addAll(remaining.asSequence())
        }
        media.clear()
        media.addAll(updated)
    }

    constructor(path: Path, library: Library) : this(library = library, metadata = GameMetadata(path = path.toString()))
}
