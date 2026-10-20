package org.gameyfin.app.core.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManagerFactory
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.event.service.spi.EventListenerRegistry
import org.hibernate.event.spi.*
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * Records which synchronized entities (games, libraries, collections) and which of their fields changed within a
 * transaction. After the commit, the collected changes are handed over to the [EntityChangeProcessor], which
 * notifies the clients.
 */
@Component
class EntityChangeTracker(
    private val entityManagerFactory: EntityManagerFactory,
    private val processor: EntityChangeProcessor
) : PostInsertEventListener,
    PostUpdateEventListener,
    PostDeleteEventListener,
    PostCollectionRecreateEventListener,
    PostCollectionUpdateEventListener,
    PostCollectionRemoveEventListener {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @PostConstruct
    fun register() {
        val registry = entityManagerFactory.unwrap(SessionFactoryImplementor::class.java)
            .serviceRegistry
            .getService(EventListenerRegistry::class.java)

        if (registry == null) {
            log.error { "Could not register entity change tracker, real-time updates are disabled" }
            return
        }

        registry.appendListeners(EventType.POST_INSERT, this)
        registry.appendListeners(EventType.POST_UPDATE, this)
        registry.appendListeners(EventType.POST_DELETE, this)
        registry.appendListeners(EventType.POST_COLLECTION_RECREATE, this)
        registry.appendListeners(EventType.POST_COLLECTION_UPDATE, this)
        registry.appendListeners(EventType.POST_COLLECTION_REMOVE, this)
    }

    /* Entity events */

    override fun onPostInsert(event: PostInsertEvent) {
        val id = event.id as? Long ?: return
        when (event.entity) {
            is Game -> record { it.game(id).created = true }
            is Library -> record { it.library(id).created = true }
            is Collection -> record { it.collection(id).created = true }
        }
    }

    override fun onPostUpdate(event: PostUpdateEvent) {
        val id = event.id as? Long ?: return
        val entity = event.entity

        when (entity) {
            is Game, is Library, is Collection -> {
                val propertyNames = event.persister.propertyNames
                val dirtyProperties = event.dirtyProperties?.map { propertyNames[it] }
                val propertyIndex = propertyNames.indexOf("metadata")

                when (entity) {
                    is Game -> {
                        val fields = EntityFieldMappings.changedGameFields(
                            dirtyProperties,
                            previousMetadata = { if (propertyIndex >= 0) event.oldState?.get(propertyIndex) else null },
                            currentMetadata = { if (propertyIndex >= 0) event.state?.get(propertyIndex) else null }
                        )
                        record { it.game(id).apply { rowUpdated = true; this.fields.addAll(fields) } }
                    }

                    is Library -> {
                        val fields = EntityFieldMappings.changedLibraryFields(dirtyProperties)
                        record { it.library(id).apply { rowUpdated = true; this.fields.addAll(fields) } }
                    }

                    is Collection -> {
                        val fields = EntityFieldMappings.changedCollectionFields(dirtyProperties)
                        record { it.collection(id).apply { rowUpdated = true; this.fields.addAll(fields) } }
                    }
                }
            }

            // Child entities, owners are resolved after the commit
            is GameFieldMetadata -> record { it.gameFieldMetadataIds.add(id) }
            is DirectoryMapping -> record { it.directoryMappingIds.add(id) }
            is Image -> record { it.imageIds.add(id) }
        }
    }

    override fun onPostDelete(event: PostDeleteEvent) {
        val id = event.id as? Long ?: return
        when (event.entity) {
            is Game -> record { it.game(id).deleted = true }
            is Library -> record { it.library(id).deleted = true }
            is Collection -> record { it.collection(id).deleted = true }
        }
    }

    /* Collection events */

    override fun onPostRecreateCollection(event: PostCollectionRecreateEvent) = onCollectionChanged(event)

    override fun onPostUpdateCollection(event: PostCollectionUpdateEvent) = onCollectionChanged(event)

    override fun onPostRemoveCollection(event: PostCollectionRemoveEvent) = onCollectionChanged(event)

    private fun onCollectionChanged(event: AbstractCollectionEvent) {
        val owner = event.affectedOwnerOrNull ?: return
        val ownerId = event.affectedOwnerIdOrNull as? Long ?: return
        val role = event.collection.role ?: return
        val property = EntityFieldMappings.propertyPathFromRole(role, event.affectedOwnerEntityName)

        when (owner) {
            is Game -> EntityFieldMappings.GAME_PROPERTIES[property]?.let { field ->
                record { it.game(ownerId).addAssociationFields(listOf(field)) }
            }

            is Library -> EntityFieldMappings.LIBRARY_PROPERTIES[property]?.let { field ->
                record { it.library(ownerId).addAssociationFields(listOf(field)) }
            }

            is Collection -> EntityFieldMappings.COLLECTION_PROPERTIES[property]?.let { field ->
                record { it.collection(ownerId).addAssociationFields(listOf(field)) }
            }
        }
    }

    /* Transaction handling */

    private fun record(action: (EntityChangeSet) -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Not bound to a Spring-managed transaction, so we cannot wait for the commit
            val changeSet = EntityChangeSet()
            action(changeSet)
            processor.enqueue(changeSet.finalizeTransaction())
            return
        }

        val synchronization = TransactionSynchronizationManager.getSynchronizations()
            .filterIsInstance<ChangeSetSynchronization>()
            .firstOrNull()
            ?: ChangeSetSynchronization(processor).also {
                TransactionSynchronizationManager.registerSynchronization(it)
            }

        action(synchronization.changeSet)
    }

    /**
     * Holds the changes of a single (physical) transaction. Suspended together with its transaction, so nested
     * `REQUIRES_NEW` transactions get their own change set.
     */
    private class ChangeSetSynchronization(private val processor: EntityChangeProcessor) : TransactionSynchronization {
        val changeSet = EntityChangeSet()

        override fun afterCommit() {
            if (!changeSet.isEmpty()) processor.enqueue(changeSet.finalizeTransaction())
        }
    }
}
