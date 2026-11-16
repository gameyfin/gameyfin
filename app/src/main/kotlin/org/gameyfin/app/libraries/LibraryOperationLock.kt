package org.gameyfin.app.libraries

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** Serializes filesystem publication and scans, including loading and committing library state. */
internal object LibraryOperationLock {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    fun <T> withLibrary(libraryId: Long, action: () -> T): T =
        locks.computeIfAbsent(libraryId) { ReentrantLock() }.withLock(action)
}
