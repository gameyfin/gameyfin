package org.gameyfin.app.collections

import jakarta.persistence.EntityManager
import org.gameyfin.app.collections.dto.SmartCollectionPreview
import org.gameyfin.app.collections.dto.SmartCollectionSettings
import org.gameyfin.app.collections.dto.SmartRule
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionKind
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.collections.repositories.findGameIdsGroupedByCollectionId
import org.gameyfin.app.core.sync.EntityChange
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.social.MemberIdentity
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant

@Service
class SmartCollectionService(
    private val collections: CollectionRepository,
    private val games: GameRepository,
    private val members: MemberIdentity,
    private val entityManager: EntityManager,
    private val json: ObjectMapper,
    transactionManager: PlatformTransactionManager
) {
    private val transaction = TransactionTemplate(transactionManager)

    @Transactional(readOnly = true)
    fun settings(id: Long): SmartCollectionSettings =
        editable(collections.findByIdOrNull(id)).settings()

    @Transactional(readOnly = true)
    fun preview(id: Long, rule: SmartRule): SmartCollectionPreview {
        editable(collections.findByIdOrNull(id))
        SmartCollectionRules.validate(rule)
        val matches = matchingIds(rule)
        val existing = memberIds(id)
        return SmartCollectionPreview(matches.size, (matches - existing).size, (existing - matches).size)
    }

    @Transactional
    fun save(id: Long, settings: SmartCollectionSettings): SmartCollectionSettings {
        val collection = editable(collections.lockById(id))
        if (settings.enabled) {
            val rule = settings.rule
                ?: requireNotNull(collection.savedRule()) { "Add a rule before enabling Smart Collection" }
            enable(collection, rule)
        } else {
            collection.smartEnabled = false
        }
        return collection.settings()
    }

    internal fun enable(collection: Collection, rule: SmartRule) {
        SmartCollectionRules.validate(rule)
        collection.smartRules = json.writeValueAsString(rule)
        reconcileAll(collection, rule)
        collection.smartEnabled = true
    }

    @EventListener(ApplicationReadyEvent::class)
    fun reconcileAtStartup() {
        reconcileGameChanges(emptyMap(), collections.findSmartIds().toSet())
    }

    internal fun reconcileGameChanges(changes: Map<Long, EntityChange<GameField>>, changedSettings: Set<Long>) {
        val relevantGames = changes.filterValues { change ->
            !change.isTransient && (change.created || change.deleted ||
                    change.fields.any(SmartCollectionRules.supportedGameFields::contains))
        }
        val affected = if (relevantGames.isEmpty()) changedSettings else changedSettings + collections.findSmartIds()
        affected.toSortedSet().forEach { id ->
            transaction.executeWithoutResult {
                val collection = collections.lockById(id)?.takeIf { it.smartEnabled } ?: return@executeWithoutResult
                val rule = requireNotNull(collection.savedRule())
                // Recheck after enabling to cover game commits concurrent with the settings transaction.
                if (id in changedSettings) {
                    reconcileAll(collection, rule)
                    return@executeWithoutResult
                }

                val fields = SmartCollectionRules.gameFields(rule)
                val candidates = relevantGames.filterValues { change ->
                    change.created || change.deleted || change.fields.any(fields::contains)
                }.keys
                candidates.chunked(500).forEach { gameIds ->
                    val existing = collections.findMatchingMemberIds(id, gameIds).toSet()
                    applyMatches(collection, existing, matchingIds(rule, gameIds))
                    // A concurrent game deletion can cascade the join row before reconciliation.
                    val deleted = gameIds.filter { changes[it]?.deleted == true }
                    if (collection.metadata.gamesAddedAt.keys.removeAll(deleted.toSet())) {
                        collection.updatedAt = Instant.now()
                    }
                }
            }
        }
    }

    private fun reconcileAll(collection: Collection, rule: SmartRule) {
        val matching = matchingIds(rule)
        applyMatches(collection, collection.id?.let(::memberIds).orEmpty(), matching)
        if (collection.metadata.gamesAddedAt.keys.retainAll(matching)) {
            collection.updatedAt = Instant.now()
        }
    }

    private fun matchingIds(rule: SmartRule, gameIds: List<Long>? = null): Set<Long> {
        val cb = entityManager.criteriaBuilder
        val query = cb.createQuery(Long::class.javaObjectType)
        val game = query.from(Game::class.java)
        val id = game.get<Long>("id")
        val match = SmartCollectionRules.specification(rule).toPredicate(game, query, cb)
        query.select(id).where(match, gameIds?.let { id.`in`(it) } ?: cb.conjunction())
        return entityManager.createQuery(query).resultList.toSet()
    }

    private fun applyMatches(collection: Collection, existing: Set<Long>, matching: Set<Long>) {
        val removed = existing - matching
        val added = matching - existing
        if (removed.isNotEmpty()) {
            collection.games.filter { it.id in removed }.forEach(collection::removeGame)
        }
        added.chunked(500).forEach { ids -> games.findAllById(ids).forEach(collection::addGame) }
    }

    private fun memberIds(id: Long): Set<Long> =
        collections.findGameIdsGroupedByCollectionId(listOf(id))[id].orEmpty().toSet()

    private fun editable(collection: Collection?): Collection {
        val viewer = members.collectionViewer()
        val allowed = when (collection?.kind) {
            CollectionKind.CURATED -> viewer.admin
            CollectionKind.CUSTOM -> viewer.memberId != null && collection.owner?.id == viewer.memberId
            else -> false
        }
        if (!allowed) throw AccessDeniedException("Collection edit access required")
        return requireNotNull(collection)
    }

    private fun Collection.savedRule(): SmartRule? =
        smartRules?.let { json.readValue(it, SmartRule::class.java) }

    private fun Collection.settings() = SmartCollectionSettings(smartEnabled, savedRule())
}
