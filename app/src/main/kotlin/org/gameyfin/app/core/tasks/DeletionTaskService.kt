package org.gameyfin.app.core.tasks

import jakarta.annotation.PostConstruct
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.pluginapi.tasks.TaskResult
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.stereotype.Service

/** Runs deletions in the background while retaining the usual transactional entity cleanup and events. */
@Service
class DeletionTaskService(
    private val registry: TaskRegistry,
    private val runService: TaskRunService,
    private val libraryService: LibraryService,
    private val collectionService: CollectionService
) {
    companion object {
        const val LIBRARY_DELETE = "core.library.delete"
        const val COLLECTION_DELETE = "core.collection.delete"
    }

    @PostConstruct
    fun registerTasks() {
        listOf(LIBRARY_DELETE to "Delete library", COLLECTION_DELETE to "Delete collection").forEach { (key, name) ->
            registry.register(
                TaskDefinition(
                    key = key,
                    name = name,
                    schedulable = false,
                    manuallyTriggerable = false,
                    cancellable = false,
                    visibleInOverview = false,
                    action = TaskAction.Dispatch { error("Deletions require an entity ID") }
                )
            )
        }
    }

    fun deleteLibrary(id: Long): TaskRunDto {
        val name = libraryService.getById(id).name
        return runService.start(LIBRARY_DELETE, TaskTrigger.MANUAL, displayName = "Delete library $name") { context ->
            context.progress("Deleting library $name")
            libraryService.delete(id)
            TaskResult("Library $name deleted")
        }
    }

    fun deleteCollection(id: Long): TaskRunDto {
        val name = collectionService.getById(id).name
        return runService.start(
            COLLECTION_DELETE,
            TaskTrigger.MANUAL,
            displayName = "Delete collection $name"
        ) { context ->
            context.progress("Deleting collection $name")
            collectionService.delete(id)
            TaskResult("Collection $name deleted")
        }
    }
}
