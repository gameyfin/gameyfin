package de.grimsi.gameyfin.games.entities

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

    val title: String,

    @OneToOne(cascade = [CascadeType.MERGE])
    val coverImage: Image? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    val comment: String? = null,

    @Lob
    @Column(columnDefinition = "CLOB")
    val summary: String? = null,

    val release: Instant? = null,

    @ManyToMany(cascade = [CascadeType.MERGE])
    val publishers: Set<Company>? = null,

    @ManyToMany(cascade = [CascadeType.MERGE])
    val developers: Set<Company>? = null,

    @ElementCollection
    val genres: Set<Genre>? = null,

    @ElementCollection
    val themes: Set<Theme>? = null,

    @ElementCollection
    val keywords: Set<String>? = null,

    @ElementCollection
    val features: Set<GameFeature>? = null,

    @ElementCollection
    val perspectives: Set<PlayerPerspective>? = null,

    @OneToMany(cascade = [CascadeType.MERGE])
    val images: Set<Image>? = null,

    @ElementCollection
    val videoUrls: Set<URI>? = null,

    @Column(unique = true)
    val path: String,

    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true)
    val metadata: Map<String, FieldMetadata> = emptyMap()
)