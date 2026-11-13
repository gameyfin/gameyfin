package org.gameyfin.app.notifications

import liquibase.integration.spring.SpringLiquibase
import org.gameyfin.app.config.SsoConfigurationChanged
import org.gameyfin.app.core.notifications.*
import org.gameyfin.pluginapi.notifications.NotificationAudience
import org.gameyfin.pluginapi.notifications.NotificationRecipients
import org.gameyfin.pluginapi.notifications.NotificationRequest
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import org.junit.jupiter.api.*
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NotificationIntegrationTest {
    @Configuration
    @EnableTransactionManagement
    class Config {
        @Bean
        fun dataSource(): DataSource =
            DriverManagerDataSource("jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "").also {
                SpringLiquibase().apply {
                    dataSource = it
                    changeLog = "classpath:/db/changelog/db.changelog-master.yaml"
                    resourceLoader = DefaultResourceLoader()
                    afterPropertiesSet()
                }
            }

        @Bean
        fun jdbc(ds: DataSource) = NamedParameterJdbcTemplate(ds)

        @Bean
        fun transactionManager(ds: DataSource) = DataSourceTransactionManager(ds)

        @Bean
        fun events() = NotificationEvents()

        @Bean
        fun store(jdbc: NamedParameterJdbcTemplate, events: NotificationEvents) =
            NotificationStore(jdbc, JsonMapper.builder().findAndAddModules().build(), events)

        @Bean
        fun system(store: NotificationStore) = SystemNotifications(store, "2.7.0-preview")
    }

    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var store: NotificationStore
    private lateinit var jdbc: NamedParameterJdbcTemplate
    private lateinit var transactions: TransactionTemplate
    private val admin = NotificationActor(1, true)
    private val otherAdmin = NotificationActor(2, true)
    private val user = NotificationActor(1, false)
    private val other = NotificationActor(2, false)

    @BeforeAll
    fun start() {
        context = AnnotationConfigApplicationContext(Config::class.java)
        store = context.getBean(NotificationStore::class.java)
        jdbc = context.getBean(NamedParameterJdbcTemplate::class.java)
        transactions = TransactionTemplate(context.getBean(DataSourceTransactionManager::class.java))
    }

    @AfterAll
    fun close() = context.close()

    @BeforeEach
    fun reset() {
        jdbc.update("DELETE FROM APP_NOTIFICATION", emptyMap<String, Any>())
        jdbc.update("DELETE FROM USERS", emptyMap<String, Any>())
        jdbc.update(
            "UPDATE NOTIFICATION_PRODUCER_STATE SET LAST_VERSION = NULL, BOOT_ID = NULL",
            emptyMap<String, Any>()
        )
        jdbc.update(
            "INSERT INTO USERS (ID, USERNAME, EMAIL, ENABLED, EMAIL_CONFIRMED) VALUES (1, 'one', 'one@example.com', true, true), (2, 'two', 'two@example.com', true, true)",
            emptyMap<String, Any>()
        )
    }

    @Test
    fun `severity filtering applies before pagination and preserves recipient isolation`() {
        repeat(3) { store.publish("core:test", NotificationRequest("Info $it", "Body")) }
        store.publish("core:test", NotificationRequest("Warning", "Body", NotificationSeverity.WARNING))
        store.publish(
            "core:test", NotificationRequest(
                "Personal error", "Body", NotificationSeverity.ERROR,
                recipients = NotificationRecipients(NotificationAudience.USERS, setOf(2))
            )
        )
        val page = store.page(admin, false, 0, 1, NotificationSeverity.WARNING)
        assertEquals(listOf("Warning"), page.map { it.title })
        assertEquals(1, store.count(admin, false, NotificationSeverity.WARNING))
        assertEquals(0, store.count(admin, false, NotificationSeverity.ERROR))
        assertEquals(4, store.count(admin, false))
        store.mutate(admin, setOf(page.single().id), false)
        assertEquals(0, store.count(admin, false, NotificationSeverity.WARNING))
        assertEquals(1, store.count(admin, true, NotificationSeverity.WARNING))
    }

    @Test
    fun `admin read and delete state is shared and hidden from regular users`() {
        store.publish("core:test", NotificationRequest("Shared", "**Markdown**"))
        val id = store.page(admin, false, 0, 25).single().id
        assertEquals(0, store.count(user, false))
        assertFailsWith<AccessDeniedException> { store.mutate(user, setOf(id), false) }
        store.mutate(admin, setOf(id), false)
        assertEquals(0, store.count(otherAdmin, false))
        assertEquals(id, store.page(otherAdmin, true, 0, 25).single().id)
        store.mutate(otherAdmin, setOf(id), true)
        assertEquals(0, store.count(admin, true))
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIFICATION", emptyMap<String, Any>(), Int::class.java)
        )
    }

    @Test
    fun `all users delivery is personal and excludes future accounts`() {
        store.publish(
            "plugin:test",
            NotificationRequest("Everyone", "Body", recipients = NotificationRecipients(NotificationAudience.ALL_USERS))
        )
        store.mutate(user, null, false)
        assertEquals(1, store.count(user, true))
        assertEquals(1, store.count(other, false))
        store.mutate(user, null, true)
        assertEquals(1, store.count(other, false))
        jdbc.update(
            "INSERT INTO USERS (ID, USERNAME, EMAIL, ENABLED, EMAIL_CONFIRMED) VALUES (3, 'three', 'three@example.com', true, true)",
            emptyMap<String, Any>()
        )
        assertEquals(0, store.count(NotificationActor(3, false), false))
        transactions.executeWithoutResult {
            jdbc.update("DELETE FROM USERS WHERE ID = 2", emptyMap<String, Any>())
            context.publishEvent(org.gameyfin.app.users.UserStateChanged(org.gameyfin.app.users.dto.UserEvent(2)))
        }
        assertEquals(
            0,
            jdbc.queryForObject("SELECT COUNT(*) FROM APP_NOTIFICATION", emptyMap<String, Any>(), Int::class.java)
        )
    }

    @Test
    fun `bulk authorization is atomic and unread deletion is refused`() {
        store.publish(
            "core:test",
            NotificationRequest(
                "Private",
                "Body",
                recipients = NotificationRecipients(NotificationAudience.USERS, setOf(1))
            )
        )
        store.publish(
            "core:test",
            NotificationRequest(
                "Other",
                "Body",
                recipients = NotificationRecipients(NotificationAudience.USERS, setOf(2))
            )
        )
        val own = store.page(user, false, 0, 25).single().id
        val foreign = store.page(other, false, 0, 25).single().id
        assertFailsWith<AccessDeniedException> { store.mutate(user, setOf(own, foreign), false) }
        assertEquals(1, store.count(user, false))
        assertFailsWith<IllegalArgumentException> { store.mutate(user, setOf(own), true) }
    }

    @Test
    fun `mark all covers all pages and retention uses read time`() {
        repeat(27) { store.publish("core:test", NotificationRequest("Item $it", "Body")) }
        assertEquals(25, store.page(admin, false, 0, 25).size)
        assertEquals(2, store.page(admin, false, 1, 25).size)
        store.mutate(admin, null, false)
        assertEquals(27, store.count(admin, true))
        val oldId = store.page(admin, true, 0, 25).first().id
        jdbc.update(
            "UPDATE NOTIFICATION_INBOX SET READ_AT = :old WHERE ID = :id",
            mapOf("id" to oldId, "old" to java.time.OffsetDateTime.now().minusDays(31))
        )
        store.publish("core:test", NotificationRequest("Unread", "Body"))
        assertEquals(1, store.cleanup(30))
        assertEquals(26, store.count(admin, true))
        assertEquals(1, store.count(admin, false))
        assertFailsWith<IllegalArgumentException> { store.cleanup(0) }
    }

    @Test
    fun `concurrent claims permit one execution and failure preserves execution identity`() {
        store.publish("core:test", NotificationRequest("Action", "Body"))
        val entry = store.page(admin, false, 0, 25).single()
        val gate = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val futures = (1..2).map {
                executor.submit(Callable {
                    gate.await(); runCatching {
                    store.claim(
                        admin,
                        entry.id,
                        "action"
                    )
                }.isSuccess
                })
            }
            gate.countDown()
            assertEquals(1, futures.count { it.get() })
        }
        assertFailsWith<IllegalArgumentException> { store.mutate(admin, setOf(entry.id), true) }
        store.finish(entry, false)
        val retried = store.claim(otherAdmin, entry.id, "action")
        assertEquals(entry.executionId, retried.executionId)
        store.finish(retried, true)
        assertEquals(1, store.count(admin, true))
        assertFailsWith<IllegalArgumentException> { store.claim(admin, entry.id, "action") }
    }

    @Test
    fun `interrupted executions never retry automatically`() {
        store.publish("core:test", NotificationRequest("Action", "Body"))
        val entry = store.page(admin, false, 0, 25).single()
        store.claim(admin, entry.id, "action")
        jdbc.update("UPDATE NOTIFICATION_INBOX SET EXECUTION_BOOT = 'previous'", emptyMap<String, Any>())
        store.recoverInterrupted()
        assertEquals("INTERRUPTED", store.entry(admin, entry.id).executionState)
        assertEquals(entry.executionId, store.claim(admin, entry.id, "action").executionId)
    }

    @Test
    fun `SSO notification participates in save transaction and survives current startup only`() {
        val system = context.getBean(SystemNotifications::class.java)
        transactions.executeWithoutResult { context.publishEvent(SsoConfigurationChanged()); it.setRollbackOnly() }
        assertEquals(0, store.count(admin, false))
        context.publishEvent(SsoConfigurationChanged())
        context.publishEvent(SsoConfigurationChanged())
        assertEquals(1, store.count(admin, false))
        system.onReady()
        assertEquals(1, store.count(admin, false))
        jdbc.update(
            "UPDATE NOTIFICATION_PRODUCER_STATE SET BOOT_ID = 'previous' WHERE ID = 'sso'",
            emptyMap<String, Any>()
        )
        system.onReady()
        assertEquals(0, store.count(admin, false))
    }

    @Test
    fun `release dates control announcements and deduplication survives user deletion`() {
        fun release(tag: String, date: String) = GithubRelease(
            tag, "https://github.com/gameyfin/gameyfin/releases/tag/$tag", java.time.Instant.parse(date), false
        )
        val system = context.getBean(SystemNotifications::class.java)
        val installed = release("v2.7.0-preview", "2026-11-02T00:00:00Z")
        val older = release("v99.0.0", "2026-11-01T00:00:00Z")
        val newer = release("v2.0.0.RC1", "2026-11-03T00:00:00Z")
        assertFalse(system.announceRelease(older, installed))
        assertFalse(system.announceRelease(installed, installed))
        assertTrue(system.announceRelease(newer, installed))
        store.mutate(admin, null, false)
        store.mutate(admin, null, true)
        assertFalse(system.announceRelease(newer.copy(version = "2.0.0.RC1"), installed))
        val latest = release("nightly", "2026-11-04T00:00:00Z")
        assertTrue(system.announceRelease(latest, installed))
        assertEquals(1, store.count(admin, false))
        transactions.executeWithoutResult { SystemNotifications(store, "nightly").onReady() }
        assertEquals(0, store.count(admin, false))
    }

    @Test
    fun `date check resolves stale notification after upgrading past announced release`() {
        fun release(tag: String, day: Int) = GithubRelease(
            tag, "https://github.com/gameyfin/gameyfin/releases/tag/$tag",
            java.time.Instant.parse("2026-11-0${day}T00:00:00Z"), false
        )
        val system = context.getBean(SystemNotifications::class.java)
        assertTrue(system.announceRelease(release("announced", 2), release("installed", 1)))
        assertEquals(1, store.count(admin, false))
        assertFalse(system.announceRelease(release("announced", 2), release("upgraded", 3)))
        assertEquals(0, store.count(admin, false))
    }

    @Test
    fun `source resolution cannot remove another plugin notification`() {
        val id = store.publish("plugin:first", NotificationRequest("First", "Body"))
        store.resolve("plugin:second", id)
        assertEquals(1, store.count(admin, false))
        store.resolve("plugin:first", id)
        assertEquals(0, store.count(admin, false))
    }

    @Test
    fun `events are scoped and published only after commit`() {
        val events = context.getBean(NotificationEvents::class.java)
        val adminEvents = java.util.concurrent.LinkedBlockingQueue<List<NotificationChange>>()
        val userEvents = java.util.concurrent.LinkedBlockingQueue<List<NotificationChange>>()
        val a = events.subscribe(admin).subscribe { adminEvents.add(it) }
        val u = events.subscribe(user).subscribe { userEvents.add(it) }
        // Drain any invalidations buffered before the first subscriber connected.
        Thread.sleep(150)
        adminEvents.clear()
        transactions.executeWithoutResult {
            store.publish("core:test", NotificationRequest("Rolled back", "Body"))
            it.setRollbackOnly()
        }
        assertNull(adminEvents.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS))
        store.publish("core:test", NotificationRequest("Committed", "Body"))
        assertNotNull(adminEvents.poll(1, java.util.concurrent.TimeUnit.SECONDS))
        assertNull(userEvents.poll(150, java.util.concurrent.TimeUnit.MILLISECONDS))
        a.dispose(); u.dispose()
    }
}
