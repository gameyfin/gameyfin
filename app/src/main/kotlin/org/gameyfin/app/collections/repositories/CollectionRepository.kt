package org.gameyfin.app.collections.repositories

import jakarta.persistence.LockModeType
import org.gameyfin.app.collections.entities.Collection
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CollectionRepository : JpaRepository<Collection, Long>, JpaSpecificationExecutor<Collection> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Collection c where c.id = :id")
    fun lockById(@Param("id") id: Long): Collection?

    @Query("select c.id from Collection c where c.smartEnabled = true order by c.id")
    fun findSmartIds(): List<Long>

    @Query("select g.id from Collection c join c.games g where c.id = :id and g.id in :gameIds")
    fun findMatchingMemberIds(@Param("id") id: Long, @Param("gameIds") gameIds: List<Long>): List<Long>

    fun findByFavouritesOwnerId(ownerId: Long): Collection?

    @Query("select c.favouritesOwnerId as memberId, g.id as gameId from Collection c join c.games g where c.favouritesOwnerId in :memberIds")
    fun favouriteGames(memberIds: List<Long>): List<FavouriteGame>

    fun findByName(name: String): Collection?

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

data class FavouriteGame(val memberId: Long, val gameId: Long)
