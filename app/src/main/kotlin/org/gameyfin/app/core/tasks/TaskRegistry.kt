package org.gameyfin.app.core.tasks

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

class TaskRegisteredEvent(val definition: TaskDefinition)
class TaskUnregisteredEvent(val definition: TaskDefinition)

/**
 * Holds the definitions of all tasks, both of Gameyfin itself and of started plugins.
 */
@Service
class TaskRegistry(private val eventPublisher: ApplicationEventPublisher) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val definitions = ConcurrentHashMap<String, TaskDefinition>()

    fun register(definition: TaskDefinition) {
        definitions[definition.key] = definition
        log.debug { "Registered task ${definition.key}" }
        eventPublisher.publishEvent(TaskRegisteredEvent(definition))
    }

    fun unregister(key: String) {
        val definition = definitions.remove(key) ?: return
        log.debug { "Unregistered task $key" }
        eventPublisher.publishEvent(TaskUnregisteredEvent(definition))
    }

    fun get(key: String): TaskDefinition? = definitions[key]

    fun require(key: String): TaskDefinition =
        get(key) ?: throw IllegalArgumentException("Unknown task: $key")

    fun getAll(): List<TaskDefinition> = definitions.values.toList()
}
