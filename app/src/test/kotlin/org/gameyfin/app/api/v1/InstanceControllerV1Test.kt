package org.gameyfin.app.api.v1

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.gameyfin.app.api.v1.controller.InstanceControllerV1
import org.gameyfin.app.api.v1.dto.HealthV1
import org.gameyfin.app.api.v1.dto.InstanceV1
import org.gameyfin.app.api.v1.dto.TaskRunV1
import org.gameyfin.app.api.v1.dto.TaskV1
import org.gameyfin.app.core.tasks.TaskRunStatus
import org.gameyfin.app.core.tasks.TaskService
import org.gameyfin.app.core.tasks.dto.TaskDto
import org.gameyfin.app.core.tasks.persistence.TaskRunEntry
import org.gameyfin.app.core.tasks.persistence.TaskRunRepository
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint
import org.springframework.boot.info.BuildProperties
import org.springframework.core.env.Environment
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceControllerV1Test {
    private val environment = mockk<Environment>()
    private val build = mockk<ObjectProvider<BuildProperties>>()
    private val health = mockk<HealthEndpoint>()
    private val tasks = mockk<TaskService>()
    private val runs = mockk<TaskRunRepository>()
    private val controller = InstanceControllerV1(allowingAccess(), environment, build, health, tasks, runs)

    @Test
    fun `instance uses build metadata when available`() {
        val time = Instant.parse("2024-02-03T04:05:06Z")
        every { build.ifAvailable } returns BuildProperties(Properties().apply {
            setProperty("version", "2.6.0")
            setProperty("time", time.toEpochMilli().toString())
            setProperty("git.commit", "abc123")
        })

        val instance = controller.instance()

        assertEquals("2.6.0", instance.version)
        assertEquals(time, instance.buildTime)
        assertEquals("abc123", instance.gitCommit)
        assertEquals(listOf("v1"), instance.apiVersions)
        assertTrue("catalog" in instance.capabilities)
    }

    @Test
    fun `instance falls back to environment and hides unknown commits`() {
        every { build.ifAvailable } returns null
        every { environment.getProperty("spring.application.version", "unknown") } returns "dev"
        assertEquals(InstanceV1("dev", null, null), controller.instance())

        every { build.ifAvailable } returns BuildProperties(Properties().apply {
            setProperty("version", "1.0"); setProperty("git.commit", "unknown")
        })
        assertNull(controller.instance().gitCommit)
    }

    @Test
    fun `health maps aggregate status to http status`() {
        every { health.health().status.code } returns "UP"
        assertEquals(200, controller.health().statusCode.value())
        assertEquals(HealthV1("UP"), controller.health().body)

        every { health.health().status.code } returns "DOWN"
        assertEquals(503, controller.health().statusCode.value())
        assertEquals("DOWN", controller.health().body!!.status)
    }

    @Test
    fun `tasks expose only public fields`() {
        val task = mockk<TaskDto> {
            every { key } returns "scan"
            every { name } returns "Scan"
            every { description } returns "Scans libraries"
            every { running } returns true
        }
        every { tasks.getTasks() } returns listOf(task)

        assertEquals(listOf(TaskV1("scan", "Scan", "Scans libraries", true)), controller.tasks())
    }

    @Test
    fun `task runs are paged and filtered by task`() {
        val started = Instant.parse("2024-01-01T00:00:00Z")
        val entry = TaskRunEntry(
            id = "run", taskKey = "scan", displayName = "Scan", link = null, trigger = TaskTrigger.MANUAL,
            status = TaskRunStatus.COMPLETED, startedAt = started, finishedAt = started.plusSeconds(5)
        )
        val spec = slot<Specification<TaskRunEntry>>()
        val pageable = slot<Pageable>()
        every { runs.findAll(capture(spec), capture(pageable)) } answers {
            PageImpl(
                listOf(entry),
                pageable.captured,
                1
            )
        }

        val page = controller.runs("scan", 0, 10, "startedAt,desc")

        assertEquals(listOf(TaskRunV1("run", "scan", "COMPLETED", started, started.plusSeconds(5))), page.items)
        assertEquals(1, page.totalElements)
        assertEquals(10, page.size)

        val root = mockk<Root<TaskRunEntry>>(relaxed = true)
        val cb = mockk<CriteriaBuilder>(relaxed = true)
        spec.captured.toPredicate(root, mockk<CriteriaQuery<*>>(relaxed = true), cb)
        io.mockk.verify { cb.equal(any(), "scan") }

        controller.runs(null, 0, 10, "id")
        spec.captured.toPredicate(root, mockk<CriteriaQuery<*>>(relaxed = true), cb)
        io.mockk.verify { cb.conjunction() }

        assertFailsWith<IllegalArgumentException> { controller.runs(null, 0, 10, "taskKey") }
    }

    @Test
    fun `single task run is returned or reported missing`() {
        every { tasks.getRun("run-1") } returns taskRun()
        every { tasks.getRun("missing") } returns null

        assertEquals("IN_PROGRESS", controller.run("run-1").status)
        assertFailsWith<NoSuchElementException> { controller.run("missing") }
    }
}
