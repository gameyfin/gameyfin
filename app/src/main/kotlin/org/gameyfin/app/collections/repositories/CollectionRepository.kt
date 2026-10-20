package org.gameyfin.app.collections.repositories

import org.gameyfin.app.collections.entities.Collection
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CollectionRepository : JpaRepository<Collection, Long> {
    fun findByName(name: String): Collection?

    /* Synchronization */

    fun findAllByUpdatedAtAfter(since: Instant): List<Collection>

    @Query("SELECT c.id FROM Collection c")
    fun findAllIds(): List<Long>

    /**
     * @return Pairs of `[collectionId, gameId]` for the given collections
     */
    @Query("SELECT c.id, g.id FROM Collection c JOIN c.games g WHERE c.id IN :collectionIds")
    fun findGameIdsByCollectionIds(@Param("collectionIds") collectionIds: kotlin.collections.Collection<Long>): List<Array<Any>>

    /**
     * Bulk update of the timestamp, e.g. if only an associated collection has changed.
     * Bypasses entity listeners.
     */
    @Modifying
    @Query("UPDATE Collection c SET c.updatedAt = :updatedAt WHERE c.id IN :ids")
    fun bumpUpdatedAt(
        @Param("ids") ids: kotlin.collections.Collection<Long>,
        @Param("updatedAt") updatedAt: Instant
    ): Int
}

/**
 * Loads the IDs of the games in the given collections without initializing the (lazy) games associations.
 */
fun CollectionRepository.findGameIdsGroupedByCollectionId(
    collectionIds: kotlin.collections.Collection<Long>
): Map<Long, List<Long>> {
    if (collectionIds.isEmpty()) return emptyMap()
    return findGameIdsByCollectionIds(collectionIds)
        .groupBy({ (it[0] as Number).toLong() }, { (it[1] as Number).toLong() })
        .mapValues { (_, gameIds) -> gameIds.sorted() }
}
