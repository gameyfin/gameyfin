package org.gameyfin.app.games.versions

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository

/** What kind of extra a store lists besides the base product. Editions that are sold as their own base product are not extras. */
enum class DlcKind {
    /** An edition that is offered as an upgrade of the base product instead of a separate product. */
    EDITION_UPGRADE,
    DLC,
    /** Soundtracks, art books and similar material, which stores sell as DLC. */
    BONUS
}

/**
 * DLC that is included in the installer(s) Gameyfin provides for a game, as offered by the store product of a link.
 * Entries are either picked by an admin or detected from installer file names.
 */
@Entity
@Table(
    name = "game_included_dlc",
    uniqueConstraints = [UniqueConstraint(columnNames = ["link_id", "dlc_id"])]
)
class GameIncludedDlc(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long? = null,

    @Column(name = "link_id", nullable = false)
    val linkId: Long,

    @Column(name = "dlc_id", nullable = false)
    val dlcId: String,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var source: LinkSource = LinkSource.MANUAL,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: DlcKind = DlcKind.DLC
)

interface GameIncludedDlcRepository : JpaRepository<GameIncludedDlc, Long> {
    fun findAllByLinkId(linkId: Long): List<GameIncludedDlc>

    fun findAllByLinkIdIn(linkIds: Collection<Long>): List<GameIncludedDlc>
}
