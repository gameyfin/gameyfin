package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameFeature
import de.grimsi.gameyfin.pluginapi.gamemetadata.Genre
import de.grimsi.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import de.grimsi.gameyfin.pluginapi.gamemetadata.Theme
import jakarta.persistence.*
import java.net.URI
import java.time.Instant

@Entity
class Game(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

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

    @ManyToMany
    var publishers: Set<Company>? = null,

    @ManyToMany
    var developers: Set<Company>? = null,

    @ElementCollection(targetClass = Genre::class)
    var genres: Set<Genre>? = null,

    @ElementCollection(targetClass = Theme::class)
    var themes: Set<Theme>? = null,

    @ElementCollection
    var keywords: Set<String>? = null,

    @ElementCollection(targetClass = GameFeature::class)
    var features: Set<GameFeature>? = null,

    @ElementCollection(targetClass = PlayerPerspective::class)
    var perspectives: Set<PlayerPerspective>? = null,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var images: Set<Image>? = null,

    @ElementCollection
    var videoUrls: Set<URI>? = null,

    @Column(unique = true)
    val path: String,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    var metadata: Map<String, FieldMetadata> = emptyMap(),

    @ElementCollection
    var originalIds: Map<PluginManagementEntry, String> = emptyMap(),

    var downloadCount: Int = 0
)