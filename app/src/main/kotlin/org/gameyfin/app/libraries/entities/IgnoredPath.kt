package org.gameyfin.app.libraries.entities

import jakarta.persistence.*
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.users.entities.User

@Entity
class IgnoredPath(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,
    @Column(unique = true, nullable = false, length = 1024)
    val path: String,
    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val source: IgnoredPathSource
) {
    fun getType(): IgnoredPathSourceType {
        return when (source) {
            is IgnoredPathPluginSource -> IgnoredPathSourceType.PLUGIN
            is IgnoredPathUserSource -> IgnoredPathSourceType.USER
            else -> throw IllegalStateException("Unknown IgnoredPathSource type")
        }
    }
}

enum class IgnoredPathSourceType {
    PLUGIN,
    USER
}

@Entity
@Inheritance
abstract class IgnoredPathSource(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    open var id: Long? = null
)

@Entity
class IgnoredPathPluginSource(
    @ManyToMany
    val plugins: MutableList<PluginManagementEntry>
) : IgnoredPathSource()

@Entity
class IgnoredPathUserSource(
    @ManyToOne
    val user: User
) : IgnoredPathSource()