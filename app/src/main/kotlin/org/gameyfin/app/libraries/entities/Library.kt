package org.gameyfin.app.libraries.entities

import jakarta.persistence.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@EntityListeners(LibraryEntityListener::class)
class Library(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null,

    var name: String,

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = [CascadeType.ALL])
    var directories: MutableList<DirectoryMapping> = ArrayList(),

    @ElementCollection(targetClass = Platform::class, fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    var platforms: MutableList<Platform> = ArrayList(),

    @OneToMany(mappedBy = "library", fetch = FetchType.EAGER, orphanRemoval = true)
    var games: MutableList<Game> = ArrayList(),

    @OneToMany(fetch = FetchType.EAGER, orphanRemoval = true, cascade = [CascadeType.ALL])
    var ignoredPaths: MutableList<IgnoredPath> = ArrayList()
)