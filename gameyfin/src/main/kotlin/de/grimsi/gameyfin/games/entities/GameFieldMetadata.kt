package de.grimsi.gameyfin.games.entities

import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.users.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
class GameFieldMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val source: GameFieldSource,

    @UpdateTimestamp
    var updatedAt: Instant? = Instant.now()
)

@Entity
@Inheritance
abstract class GameFieldSource(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    open var id: Long? = null
)

@Entity
class GameFieldPluginSource(
    @ManyToOne
    val plugin: PluginManagementEntry
) : GameFieldSource()

@Entity
class GameFieldUserSource(
    @ManyToOne
    val user: User
) : GameFieldSource()