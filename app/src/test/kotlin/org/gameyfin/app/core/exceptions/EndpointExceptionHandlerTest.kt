package org.gameyfin.app.core.exceptions

import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import com.vaadin.hilla.EndpointInvocationException.EndpointForbiddenException
import com.vaadin.hilla.EndpointInvocationException.EndpointUnauthorizedException
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test endpoint to verify exception handling
 */
@Endpoint
@Component
class TestEndpoint {
    fun throwRuntimeException() {
        throw RuntimeException("Test runtime exception")
    }

    fun throwIllegalArgumentException() {
        throw IllegalArgumentException("Test illegal argument")
    }

    fun throwEndpointException() {
        throw EndpointException("Already an endpoint exception")
    }

    fun denyAccess() {
        throw AccessDeniedException("Login required")
    }

    fun returnNormally(): String {
        return "Success"
    }
}

@Configuration
@EnableAspectJAutoProxy
@Import(TestEndpoint::class, EndpointExceptionHandler::class)
class TestConfig

@SpringBootTest(classes = [TestConfig::class])
class EndpointExceptionHandlerTest {

    @Autowired
    private lateinit var testEndpoint: TestEndpoint

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `access denials return 401 for absent anonymous and unauthenticated users without logging`() {
        val logger = LoggerFactory.getLogger(EndpointExceptionHandler::class.java) as Logger
        val logs = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(logs)
        try {
            val authentications = listOf(
                null,
                AnonymousAuthenticationToken("test", "custom-anonymous", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
                UsernamePasswordAuthenticationToken.unauthenticated("user", "unused")
            )
            for (authentication in authentications) {
                SecurityContextHolder.getContext().authentication = authentication
                val exception = assertFailsWith<EndpointUnauthorizedException> { testEndpoint.denyAccess() }
                assertEquals(401, exception.httpStatusCode)
                assertEquals("Login required", exception.message)
            }
            assertTrue(logs.list.isEmpty(), "Expected access denials must not be logged")
        } finally {
            logger.detachAppender(logs)
            logs.stop()
        }
    }

    @Test
    fun `authenticated access denials return 403`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken.authenticated(
            "user", "unused", listOf(SimpleGrantedAuthority("ROLE_USER")))
        val exception = assertFailsWith<EndpointForbiddenException> { testEndpoint.denyAccess() }
        assertEquals(403, exception.httpStatusCode)
    }

    @Test
    fun `should wrap RuntimeException in EndpointException`() {
        val exception = assertFailsWith<EndpointException> {
            testEndpoint.throwRuntimeException()
        }
        assertEquals("Test runtime exception", exception.message)
    }

    @Test
    fun `should wrap IllegalArgumentException in EndpointException`() {
        val exception = assertFailsWith<EndpointException> {
            testEndpoint.throwIllegalArgumentException()
        }
        assertEquals("Test illegal argument", exception.message)
    }

    @Test
    fun `should re-throw EndpointException as-is`() {
        val exception = assertFailsWith<EndpointException> {
            testEndpoint.throwEndpointException()
        }
        assertEquals("Already an endpoint exception", exception.message)
    }

    @Test
    fun `should not interfere with normal execution`() {
        val result = testEndpoint.returnNormally()
        assertEquals("Success", result)
    }
}

