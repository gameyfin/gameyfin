package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameFeature
import de.grimsi.gameyfin.pluginapi.gamemetadata.Genre
import de.grimsi.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import de.grimsi.gameyfin.pluginapi.gamemetadata.Theme
import jakarta.persistence.*
import java.net.URL
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

    @OneToMany(cascade = [CascadeType.MERGE])
    val publishers: Set<Company>,

    @OneToMany(cascade = [CascadeType.MERGE])
    val developers: Set<Company>,

    @ElementCollection
    val genres: Set<Genre>,

    @ElementCollection
    val themes: Set<Theme>,

    @ElementCollection
    val keywords: Set<String>,

    @ElementCollection
    val features: Set<GameFeature>,

    @ElementCollection
    val perspectives: Set<PlayerPerspective>,

    @OneToMany(cascade = [CascadeType.MERGE])
    val screenshots: Set<Screenshot>,

    @ElementCollection
    val videoUrls: Set<URL>,

    @Column(unique = true)
    val path: String,

    @ManyToOne
    val source: PluginManagementEntry
)