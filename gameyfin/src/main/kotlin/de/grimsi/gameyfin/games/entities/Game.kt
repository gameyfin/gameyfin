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
    var comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    var summary: String? = null,

    var release: Instant? = null,

    var userRating: Int? = null,

    var criticRating: Int? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    var publishers: List<Company> = emptyList(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH])
    var developers: List<Company> = emptyList(),

    @ElementCollection(targetClass = Genre::class)
    var genres: List<Genre> = emptyList(),

    @ElementCollection(targetClass = Theme::class)
    var themes: List<Theme> = emptyList(),

    @ElementCollection
    var keywords: List<String> = emptyList(),

    @ElementCollection(targetClass = GameFeature::class)
    var features: List<GameFeature> = emptyList(),

    @ElementCollection(targetClass = PlayerPerspective::class)
    var perspectives: List<PlayerPerspective>? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var images: List<Image> = emptyList(),

    @ElementCollection
    var videoUrls: List<URI> = emptyList(),

    @Column(unique = true)
    val path: String,

    var fileSize: Long? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var metadata: Map<String, FieldMetadata> = emptyMap(),

    @ElementCollection
    var originalIds: Map<PluginManagementEntry, String> = emptyMap(),

    var downloadCount: Int = 0
)