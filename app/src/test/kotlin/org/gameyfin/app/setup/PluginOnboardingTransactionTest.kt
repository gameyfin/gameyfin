package org.gameyfin.app.setup

import io.mockk.*
import org.gameyfin.app.db.ApplicationJpaTestConfig
import org.gameyfin.app.users.RoleService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.*
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import kotlin.test.*

class PluginOnboardingTransactionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = [PluginOnboardingRepository::class, UserRepository::class])
    @Import(ApplicationJpaTestConfig::class, SetupService::class)
    class Transactions {
        @Bean
        fun users(repository: UserRepository): UserService = mockk<UserService>().also {
            every { it.registerOrUpdateUser(any()) } answers { repository.saveAndFlush(firstArg<User>()) }
        }

        @Bean
        fun roles(): RoleService = mockk(relaxed = true)

        @Bean
        fun onboarding(repository: PluginOnboardingRepository): PluginOnboardingService {
            val state = mockk<PluginOnboardingRepository>()
            every { state.findById(1) } answers { repository.findById(1) }
            every { state.save(any<PluginOnboardingState>()) } throws IllegalStateException("state write failed") andThenAnswer {
                repository.save(firstArg<PluginOnboardingState>())
            }
            return PluginOnboardingService(state)
        }
    }

    @Test
    fun `initial admin and pending setup commit together and rollback together`() {
        AnnotationConfigApplicationContext(Transactions::class.java).use { context ->
            val setup = context.getBean(SetupService::class.java)
            val onboarding = context.getBean(PluginOnboardingService::class.java)
            val users = context.getBean(UserRepository::class.java)
            val registration = UserRegistrationDto("admin", "password", "a@example.com")
            assertFalse(onboarding.isPending(), "Existing installations have no onboarding row")
            assertFailsWith<IllegalStateException> { setup.createInitialAdminUser(registration) }
            assertEquals(0, users.count())
            assertFalse(onboarding.isPending())
            setup.createInitialAdminUser(registration)
            assertEquals(1, users.count())
            assertTrue(onboarding.isPending())
            onboarding.complete()
            onboarding.complete()
            assertFalse(onboarding.isPending())
        }
    }
}
