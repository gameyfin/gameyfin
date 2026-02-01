package org.gameyfin.app.core.security

import io.mockk.*
import org.gameyfin.app.config.ConfigProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.getBean
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotatedTypeMetadata
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SsoEnabledConditionTest {

    private lateinit var condition: SsoEnabledCondition
    private lateinit var context: ConditionContext
    private lateinit var metadata: AnnotatedTypeMetadata
    private lateinit var environment: Environment

    @BeforeEach
    fun setup() {
        condition = SsoEnabledCondition()
        context = mockk<ConditionContext>()
        metadata = mockk<AnnotatedTypeMetadata>()
        environment = mockk<Environment>()

        every { context.beanFactory } returns mockk {
            every { getBean<Environment>() } returns environment
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `matches should return false when beanFactory is null`() {
        every { context.beanFactory } returns null

        val result = condition.matches(context, metadata)

        assertFalse(result)
    }

    @Test
    fun `matches should return false when exception occurs`() {
        every { environment.getProperty("spring.datasource.url") } throws RuntimeException("Test exception")

        val result = condition.matches(context, metadata)

        assertFalse(result)
    }

    @Test
    fun `matches should return false when datasource url is null`() {
        every { environment.getProperty("spring.datasource.url") } returns null
        every { environment.getProperty("spring.datasource.username") } returns "user"
        every { environment.getProperty("spring.datasource.password") } returns "pass"

        val result = condition.matches(context, metadata)

        assertFalse(result)
    }

    @Test
    fun `matches should return false when no result is found in database`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<PreparedStatement>()
        val resultSet = mockk<ResultSet>()

        every { environment.getProperty("spring.datasource.url") } returns "jdbc:h2:mem:test"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)
        every { DriverManager.getConnection(any(), any(), any()) } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { statement.setString(any(), any()) } just Runs
        every { statement.executeQuery() } returns resultSet
        every { resultSet.next() } returns false

        val result = condition.matches(context, metadata)

        assertFalse(result)
        verify { connection.close() }
    }

    @Test
    fun `matches should return true when SSO is enabled in database`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<PreparedStatement>()
        val resultSet = mockk<ResultSet>()
        val encryptedTrue = "encrypted_true_value"

        every { environment.getProperty("spring.datasource.url") } returns "jdbc:h2:mem:test"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)
        mockkObject(EncryptionUtils)

        every { DriverManager.getConnection(any(), any(), any()) } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { statement.setString(1, ConfigProperties.SSO.OIDC.Enabled.key) } just Runs
        every { statement.executeQuery() } returns resultSet
        every { resultSet.next() } returns true
        every { resultSet.getString("value") } returns encryptedTrue
        every { EncryptionUtils.decrypt(encryptedTrue) } returns "true"

        val result = condition.matches(context, metadata)

        assertTrue(result)
        verify(exactly = 1) { statement.setString(1, ConfigProperties.SSO.OIDC.Enabled.key) }
        verify(exactly = 1) { EncryptionUtils.decrypt(encryptedTrue) }
        verify { connection.close() }
    }

    @Test
    fun `matches should return false when SSO is disabled in database`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<PreparedStatement>()
        val resultSet = mockk<ResultSet>()
        val encryptedFalse = "encrypted_false_value"

        every { environment.getProperty("spring.datasource.url") } returns "jdbc:h2:mem:test"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)
        mockkObject(EncryptionUtils)

        every { DriverManager.getConnection(any(), any(), any()) } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { statement.setString(1, ConfigProperties.SSO.OIDC.Enabled.key) } just Runs
        every { statement.executeQuery() } returns resultSet
        every { resultSet.next() } returns true
        every { resultSet.getString("value") } returns encryptedFalse
        every { EncryptionUtils.decrypt(encryptedFalse) } returns "false"

        val result = condition.matches(context, metadata)

        assertFalse(result)
        verify { connection.close() }
    }

    @Test
    fun `matches should return false when decryption fails`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<PreparedStatement>()
        val resultSet = mockk<ResultSet>()

        every { environment.getProperty("spring.datasource.url") } returns "jdbc:h2:mem:test"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)
        mockkObject(EncryptionUtils)

        every { DriverManager.getConnection(any(), any(), any()) } returns connection
        every { connection.prepareStatement(any()) } returns statement
        every { statement.setString(any(), any()) } just Runs
        every { statement.executeQuery() } returns resultSet
        every { resultSet.next() } returns true
        every { resultSet.getString("value") } returns "encrypted"
        every { EncryptionUtils.decrypt(any()) } throws RuntimeException("Decryption failed")

        val result = condition.matches(context, metadata)

        assertFalse(result)
    }

    @Test
    fun `matches should return false when database connection fails`() {
        every { environment.getProperty("spring.datasource.url") } returns "jdbc:invalid:url"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)
        every { DriverManager.getConnection(any(), any(), any()) } throws RuntimeException("Connection failed")

        val result = condition.matches(context, metadata)

        assertFalse(result)
    }

    @Suppress("SqlSourceToSinkFlow")
    @Test
    fun `matches should use correct SQL query`() {
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<PreparedStatement>()
        val resultSet = mockk<ResultSet>()

        every { environment.getProperty("spring.datasource.url") } returns "jdbc:h2:mem:test"
        every { environment.getProperty("spring.datasource.username") } returns "sa"
        every { environment.getProperty("spring.datasource.password") } returns ""

        mockkStatic(DriverManager::class)

        val capturedSql = slot<String>()
        every { DriverManager.getConnection(any(), any(), any()) } returns connection
        every { connection.prepareStatement(capture(capturedSql)) } returns statement
        every { statement.setString(any(), any()) } just Runs
        every { statement.executeQuery() } returns resultSet
        every { resultSet.next() } returns false

        condition.matches(context, metadata)

        assertTrue(capturedSql.captured.contains("SELECT"))
        assertTrue(capturedSql.captured.contains("app_config"))
        assertTrue(capturedSql.captured.contains("value"))
        assertTrue(capturedSql.captured.contains("key"))
    }
}

