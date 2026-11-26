package org.gameyfin.app.users

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.token.TokenRepository
import org.gameyfin.app.core.token.TokenType
import org.gameyfin.app.core.token.TokenValidationResult
import org.gameyfin.app.db.ApplicationJpaTestConfig
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.dto.UserUpdateDto
import org.gameyfin.app.users.emailconfirmation.EmailConfirmationService
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OptionalEmailIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    @EnableJpaRepositories(basePackageClasses = [UserRepository::class, TokenRepository::class])
    @Import(ApplicationJpaTestConfig::class, UserService::class, EmailConfirmationService::class)
    class AccountConfig {
        @Bean fun imageService(): ImageService = mockk()
        @Bean fun roleService(): RoleService = mockk()
        @Bean fun sessionService(): SessionService = mockk()
        @Bean fun passwordEncoder() = BCryptPasswordEncoder()

        @Bean
        fun config(): ConfigService = mockk {
            every { get(ConfigProperties.Users.SignUps.Allow) } returns true
            every { get(ConfigProperties.Users.SignUps.ConfirmationRequired) } returns false
            every { get(ConfigProperties.Users.EmailRequired) } returns false
        }
    }

    @Test
    fun `accounts without email can coexist and address changes invalidate confirmation links`() {
        AnnotationConfigApplicationContext(AccountConfig::class.java).use { context ->
            val users = context.getBean(UserService::class.java)
            val confirmations = context.getBean(EmailConfirmationService::class.java)
            val tokens = context.getBean(TokenRepository::class.java)
            val config = context.getBean(ConfigService::class.java)

            RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest()))
            try {
                users.selfRegisterUser(UserRegistrationDto("alex", "password"))
                users.selfRegisterUser(UserRegistrationDto("sam", "password", ""))
                assertNull(users.getByUsernameNonNull("alex").email)
                assertNull(users.getByUsernameNonNull("sam").email)

                fun changeEmail(email: String) =
                    users.updateUser("alex", UserUpdateDto(username = null, password = null, email = email))

                fun pendingSecret(): String {
                    val user = users.getByUsernameNonNull("alex")
                    assertFalse(user.emailConfirmed)
                    return assertNotNull(tokens.findByCreatorAndType(user, TokenType.EmailConfirmation)).secret
                }

                changeEmail("first@example.com")
                val original = pendingSecret()
                changeEmail("second@example.com")
                val replacement = pendingSecret()
                assertEquals(TokenValidationResult.INVALID, confirmations.confirmEmail(original))
                changeEmail("")
                assertEquals(TokenValidationResult.INVALID, confirmations.confirmEmail(replacement))
                assertNull(users.getByUsernameNonNull("alex").email)

                changeEmail("second@example.com")
                assertEquals(TokenValidationResult.VALID, confirmations.confirmEmail(pendingSecret()))
                assertTrue(users.getByUsernameNonNull("alex").emailConfirmed)

                every { config.get(ConfigProperties.Users.EmailRequired) } returns true
                assertTrue(users.loadUserByUsername("sam").isEnabled)
                users.updateUser("sam", UserUpdateDto(username = "sam-renamed", password = null, email = null))
                assertNull(users.getByUsernameNonNull("sam-renamed").email)
            } finally {
                RequestContextHolder.resetRequestAttributes()
            }
        }
    }
}
