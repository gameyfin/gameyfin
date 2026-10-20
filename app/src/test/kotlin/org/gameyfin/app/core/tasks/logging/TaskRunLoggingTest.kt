package org.gameyfin.app.core.tasks.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.core.spi.FilterReply
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskRunLoggingTest {

    @AfterEach
    fun cleanup() = MDC.clear()

    private fun event(runId: String?): LoggingEvent {
        val logger = LoggerContext().getLogger("test")
        return LoggingEvent("fqcn", logger, Level.INFO, "msg", null, null).apply {
            mdcPropertyMap = if (runId == null) emptyMap() else mapOf(TASK_RUN_MDC_KEY to runId)
        }
    }

    @Test
    fun `filters are complementary`() {
        assertEquals(FilterReply.NEUTRAL, TaskRunLogFilter().decide(event("run-1")))
        assertEquals(FilterReply.DENY, TaskRunLogFilter().decide(event(null)))
        assertEquals(FilterReply.DENY, NonTaskRunLogFilter().decide(event("run-1")))
        assertEquals(FilterReply.NEUTRAL, NonTaskRunLogFilter().decide(event(null)))
    }

    @Test
    fun `withTaskRun sets and restores the run id`() {
        MDC.put(TASK_RUN_MDC_KEY, "outer")
        MdcPropagation.withTaskRun("inner") { assertEquals("inner", MDC.get(TASK_RUN_MDC_KEY)) }
        assertEquals("outer", MDC.get(TASK_RUN_MDC_KEY))
    }

    @Test
    fun `wrap propagates the MDC to another thread`() {
        Executors.newSingleThreadExecutor().use { executor ->
            val seen = MdcPropagation.withTaskRun("run-1") {
                executor.submit(MdcPropagation.wrap(Callable { MDC.get(TASK_RUN_MDC_KEY) })).get()
            }
            assertEquals("run-1", seen)
        }
    }

    @Test
    fun `wrap works without MDC context`() {
        MDC.clear()
        var seen: String? = "x"
        MdcPropagation.wrap(Runnable { seen = MDC.get(TASK_RUN_MDC_KEY) }).run()
        assertNull(seen)
    }

    @Test
    fun `submitWithMdc keeps the run id`() {
        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val seen = MdcPropagation.withTaskRun("run-2") {
                executor.submitWithMdc { MDC.get(TASK_RUN_MDC_KEY) }.get()
            }
            assertEquals("run-2", seen)
        }
    }
}
