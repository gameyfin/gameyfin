package org.gameyfin.app.core.tasks.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import org.slf4j.MDC
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

const val TASK_RUN_MDC_KEY = "taskRunId"

/**
 * Only lets log events pass which have been logged during a task run, used for the per-run log files
 * (see `log-config-template.xml`).
 */
class TaskRunLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply =
        if (event.mdcPropertyMap[TASK_RUN_MDC_KEY].isNullOrEmpty()) FilterReply.DENY else FilterReply.NEUTRAL
}

/**
 * Counterpart of [TaskRunLogFilter]: only lets log events pass which have NOT been logged during a task run, so
 * task runs don't spam the console (they are written to their own log file instead).
 */
class NonTaskRunLogFilter : Filter<ILoggingEvent>() {
    override fun decide(event: ILoggingEvent): FilterReply =
        if (event.mdcPropertyMap[TASK_RUN_MDC_KEY].isNullOrEmpty()) FilterReply.NEUTRAL else FilterReply.DENY
}

/** Submits [action] to this executor, keeping the MDC (and with it the current task run) of the calling thread. */
fun <T> ExecutorService.submitWithMdc(action: () -> T): Future<T> =
    submit(MdcPropagation.wrap(Callable { action() }))

/**
 * Propagates the MDC (and with it the ID of the current task run) to other threads.
 */
object MdcPropagation {

    fun wrap(runnable: Runnable): Runnable {
        val context = MDC.getCopyOfContextMap()?.toMap()
        return Runnable { withContext(context) { runnable.run() } }
    }

    fun <T> wrap(callable: Callable<T>): Callable<T> {
        val context = MDC.getCopyOfContextMap()?.toMap()
        return Callable { withContext(context) { callable.call() } }
    }

    /** Executes [action] with the given task run ID in the MDC */
    fun <T> withTaskRun(runId: String, action: () -> T): T {
        val previous = MDC.get(TASK_RUN_MDC_KEY)
        MDC.put(TASK_RUN_MDC_KEY, runId)
        try {
            return action()
        } finally {
            if (previous == null) MDC.remove(TASK_RUN_MDC_KEY) else MDC.put(TASK_RUN_MDC_KEY, previous)
        }
    }

    private fun <T> withContext(context: Map<String, String>?, action: () -> T): T {
        val previous = MDC.getCopyOfContextMap()
        if (context == null) MDC.clear() else MDC.setContextMap(context)
        try {
            return action()
        } finally {
            if (previous == null) MDC.clear() else MDC.setContextMap(previous)
        }
    }
}
