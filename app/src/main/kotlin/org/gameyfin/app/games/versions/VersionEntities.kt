package org.gameyfin.app.games.versions

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** How an installed version was determined. Manual values are never overwritten by automation. */
enum class LinkSource {
    AUTO,
    MANUAL
}

/**
 * Connects a game to a product on a store. Created by an admin and completely independent of metadata plugins
 * and of the metadata provider used to match the game, so any game can be linked to any store.
 */
@Entity
@Table(
    name = "game_store_link",
    uniqueConstraints = [UniqueConstraint(columnNames = ["game_id", "store", "store_app_id"])]
)
class GameStoreLink(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "game_id", nullable = false)
    val gameId: Long,

    /** ID of the store, as declared by the game store plugin */
    @Column(nullable = false)
    val store: String,

    @Column(name = "store_app_id", nullable = false)
    val storeAppId: String,

    /** The link used for update checks. At most one per game. */
    @Column(nullable = false)
    var tracked: Boolean = false,

    @Column(name = "versions_checked_at")
    var versionsCheckedAt: Instant? = null,

    /** Free-text label such as "Anniversary Edition". Purely informational, it does not affect version checks. */
    @Column(length = 100)
    var edition: String? = null
)

/** Cached version/build of a store product. Steam only has build IDs, GOG has real version names. */
@Entity
@Table(
    name = "store_version",
    uniqueConstraints = [UniqueConstraint(columnNames = ["link_id", "build_id", "branch"])]
)
class StoreVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "link_id", nullable = false)
    val linkId: Long,

    @Column(name = "build_id", nullable = false)
    val buildId: String,

    /** Empty string for the default/public branch. */
    @Column(nullable = false)
    val branch: String = "",

    @Column(nullable = false)
    var label: String,

    @Column(name = "released_at")
    var releasedAt: Instant? = null
)

@Entity
@Table(name = "game_version_state")
class GameVersionState(
    @Id
    @Column(name = "link_id")
    val linkId: Long,

    @Column(name = "installed_version_id")
    var installedVersionId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: LinkSource = LinkSource.MANUAL
)

interface GameStoreLinkRepository : JpaRepository<GameStoreLink, Long> {
    fun findAllByGameId(gameId: Long): List<GameStoreLink>

    fun findByGameIdAndStoreAndStoreAppId(gameId: Long, store: String, storeAppId: String): GameStoreLink?
}

interface StoreVersionRepository : JpaRepository<StoreVersion, Long> {
    fun findAllByLinkId(linkId: Long): List<StoreVersion>

    fun findByIdAndLinkId(id: Long, linkId: Long): StoreVersion?
}

interface GameVersionStateRepository : JpaRepository<GameVersionState, Long>
