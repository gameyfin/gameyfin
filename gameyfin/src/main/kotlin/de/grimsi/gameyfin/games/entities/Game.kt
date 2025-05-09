package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.libraries.Library
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameFeature
import de.grimsi.gameyfin.pluginapi.gamemetadata.Genre
import de.grimsi.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import de.grimsi.gameyfin.pluginapi.gamemetadata.Theme
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.net.URI
import java.time.Instant

@Entity
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @CreationTimestamp
    @Column(updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    var updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_id")
    val library: Library,

    var title: String? = null,

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    var coverImage: Image? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    val comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    var summary: String? = null,

    var release: Instant? = null,

    var userRating: Int? = null,

    var criticRating: Int? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    var publishers: Set<Company> = emptySet(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    var developers: Set<Company> = emptySet(),

    @ElementCollection(targetClass = Genre::class)
    var genres: Set<Genre> = emptySet(),

    @ElementCollection(targetClass = Theme::class)
    var themes: Set<Theme> = emptySet(),

    @ElementCollection
    var keywords: Set<String> = emptySet(),

    @ElementCollection(targetClass = GameFeature::class)
    var features: Set<GameFeature> = emptySet(),

    @ElementCollection(targetClass = PlayerPerspective::class)
    var perspectives: Set<PlayerPerspective>? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var images: Set<Image> = emptySet(),

    @ElementCollection
    var videoUrls: Set<URI> = emptySet(),

    @Column(unique = true)
    val path: String,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var metadata: Map<String, FieldMetadata> = emptyMap(),

    @ElementCollection
    var originalIds: Map<PluginManagementEntry, String> = emptyMap(),

    var downloadCount: Int = 0
)