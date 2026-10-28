package org.gameyfin.app.core.tasks.persistence

import jakarta.persistence.*
import org.gameyfin.app.core.tasks.TaskRunStatus
import org.gameyfin.pluginapi.tasks.TaskTrigger
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "task_schedule")
class TaskScheduleEntry(
    @Id
    @Column(name = "task_key")
    val taskKey: String,

    var cron: String?,

    var enabled: Boolean
)

interface TaskScheduleRepository : JpaRepository<TaskScheduleEntry, String>

@Entity
@Table(name = "task_setting")
class TaskSettingEntry(
    @EmbeddedId
    val id: TaskSettingKey,

    @Column(name = "setting_value", length = 4000)
    var value: String?
)

@Embeddable
data class TaskSettingKey(
    @Column(name = "task_key")
    val taskKey: String,

    @Column(name = "setting_key")
    val settingKey: String
) : Serializable

interface TaskSettingRepository : JpaRepository<TaskSettingEntry, TaskSettingKey> {
    fun findAllByIdTaskKey(taskKey: String): List<TaskSettingEntry>
}

@Entity
@Table(name = "task_run")
class TaskRunEntry(
    @Id
    @Column(length = 36)
    val id: String,

    @Column(name = "task_key", nullable = false)
    val taskKey: String,

    @Column(name = "display_name", nullable = false, length = 1000)
    var displayName: String,

    @Column(length = 1000)
    var link: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    val trigger: TaskTrigger,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TaskRunStatus,

    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,

    @Column(name = "finished_at")
    var finishedAt: Instant? = null,

    @Column(length = 4000)
    var summary: String? = null,

    @Column(name = "error_message", length = 4000)
    var errorMessage: String? = null
)

interface TaskRunRepository : JpaRepository<TaskRunEntry, String> {
    fun findAllByTaskKeyOrderByStartedAtDesc(taskKey: String, pageable: Pageable): Slice<TaskRunEntry>

    fun findAllByOrderByStartedAtDesc(pageable: Pageable): Slice<TaskRunEntry>

    fun findFirstByTaskKeyOrderByStartedAtDesc(taskKey: String): TaskRunEntry?

    fun findAllByStatus(status: TaskRunStatus): List<TaskRunEntry>

    fun findAllByFinishedAtAfterOrderByStartedAtDesc(after: Instant, pageable: Pageable): List<TaskRunEntry>

    fun findAllByStartedAtBefore(before: Instant): List<TaskRunEntry>

    @Query("SELECT r.id FROM TaskRunEntry r WHERE r.taskKey = :taskKey AND r.status <> :excludedStatus ORDER BY r.startedAt DESC")
    fun findIdsByTaskKeyAndStatusNotOrderByStartedAtDesc(taskKey: String, excludedStatus: TaskRunStatus): List<String>

    @Query("SELECT r.id FROM TaskRunEntry r")
    fun findAllIds(): List<String>

    @Query("SELECT DISTINCT r.taskKey FROM TaskRunEntry r")
    fun findAllTaskKeys(): List<String>

    @Modifying
    @Transactional
    @Query("DELETE FROM TaskRunEntry r WHERE r.id IN :ids")
    fun deleteAllByIdIn(ids: Collection<String>): Int
}
