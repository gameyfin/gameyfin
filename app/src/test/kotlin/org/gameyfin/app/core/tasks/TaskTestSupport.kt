package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/** Task run repository backed by a map, sufficient for the queries used by the [TaskRunService] */
fun inMemoryTaskRunRepository(store: MutableMap<String, TaskRunEntry> = ConcurrentHashMap()): TaskRunRepository {
    val repository = mockk<TaskRunRepository>(relaxed = true)
    every { repository.save(any()) } answers { firstArg<TaskRunEntry>().also { store[it.id] = it } }
    every { repository.findById(any()) } answers { Optional.ofNullable(store[firstArg()]) }
    every { repository.findAllByStatus(any()) } answers {
        val status = firstArg<TaskRunStatus>()
        store.values.filter { it.status == status }
    }
    every { repository.findAllByFinishedAtAfterOrderByStartedAtDesc(any(), any()) } returns emptyList()
    return repository
}

fun taskDefinition(
    key: String,
    schedulable: Boolean = true,
    manuallyTriggerable: Boolean = true,
    cancellable: Boolean = true,
    defaultSchedule: String? = null,
    scheduledByDefault: Boolean = defaultSchedule != null,
    action: TaskAction = TaskAction.Run { null }
) = TaskDefinition(
    key = key,
    name = "Task $key",
    defaultSchedule = defaultSchedule,
    scheduledByDefault = scheduledByDefault,
    schedulable = schedulable,
    manuallyTriggerable = manuallyTriggerable,
    cancellable = cancellable,
    action = action
)
