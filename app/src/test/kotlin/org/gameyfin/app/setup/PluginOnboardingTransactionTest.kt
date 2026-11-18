package org.gameyfin.app.setup

import io.mockk.*
import org.gameyfin.app.users.RoleService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.Optional
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.*

class PluginOnboardingTransactionTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    class Transactions

    @Test
    fun `initial admin and pending setup commit together and rollback together`() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:onboarding-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("CREATE TABLE ADMIN_RECORD (ID INTEGER PRIMARY KEY)")
        jdbc.execute("CREATE TABLE PLUGIN_ONBOARDING_STATE (ID INTEGER PRIMARY KEY, PENDING BOOLEAN NOT NULL)")
        val users = mockk<UserService>()
        every { users.registerOrUpdateUser(any()) } answers {
            jdbc.update("INSERT INTO ADMIN_RECORD VALUES (1)")
            firstArg<User>().also { it.id = 1 }
        }
        val repository = mockk<PluginOnboardingRepository>()
        every { repository.findById(1) } answers {
            Optional.ofNullable(jdbc.query("SELECT PENDING FROM PLUGIN_ONBOARDING_STATE WHERE ID=1") { rs, _ ->
                PluginOnboardingState(pending = rs.getBoolean(1))
            }.firstOrNull())
        }
        AnnotationConfigApplicationContext().use { context ->
            context.register(Transactions::class.java)
            context.registerBean(DataSource::class.java, java.util.function.Supplier { dataSource })
            context.registerBean(PlatformTransactionManager::class.java, java.util.function.Supplier { DataSourceTransactionManager(dataSource) })
            context.registerBean(UserService::class.java, java.util.function.Supplier { users })
            context.registerBean(RoleService::class.java, java.util.function.Supplier { mockk(relaxed = true) })
            context.registerBean(PluginOnboardingRepository::class.java, java.util.function.Supplier { repository })
            context.register(PluginOnboardingService::class.java, SetupService::class.java)
            context.refresh()
            val setup = context.getBean(SetupService::class.java)
            val onboarding = context.getBean(PluginOnboardingService::class.java)
            assertFalse(onboarding.isPending(), "Existing installations have no onboarding row")
            every { repository.save(any<PluginOnboardingState>()) } throws IllegalStateException("state write failed")
            assertFailsWith<IllegalStateException> { setup.createInitialAdminUser(UserRegistrationDto("admin", "password", "a@example.com")) }
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ADMIN_RECORD", Int::class.java))
            assertFalse(onboarding.isPending())
            every { repository.save(any<PluginOnboardingState>()) } answers {
                firstArg<PluginOnboardingState>().also {
                    jdbc.update("MERGE INTO PLUGIN_ONBOARDING_STATE KEY(ID) VALUES (?, ?)", it.id, it.pending)
                }
            }
            setup.createInitialAdminUser(UserRegistrationDto("admin", "password", "a@example.com"))
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ADMIN_RECORD", Int::class.java))
            assertTrue(onboarding.isPending())
            onboarding.complete()
            onboarding.complete()
            assertFalse(onboarding.isPending())
        }
    }
}
