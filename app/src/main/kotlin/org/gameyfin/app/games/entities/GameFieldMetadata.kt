package org.gameyfin.app.games.entities

import jakarta.persistence.*
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.users.entities.User
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
class GameFieldMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var source: GameFieldSource,

    @UpdateTimestamp
    var updatedAt: Instant? = Instant.now()
)

@Entity
@Inheritance
abstract class GameFieldSource(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO) var id: Long? = null
)

@Entity
class GameFieldPluginSource(
    @ManyToOne
    var plugin: PluginManagementEntry
) : GameFieldSource()

@Entity
class GameFieldUserSource(
    @ManyToOne
    var user: User
) : GameFieldSource()

/** Historical attribution remains available after credential revocation or creator deletion. */
@Entity
class GameFieldApiSource(
    @Column(name = "api_credential_id") var credentialId: String,
    @Column(name = "api_credential_name", length = 100) var credentialName: String
) : GameFieldSource()
