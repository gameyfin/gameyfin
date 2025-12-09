package org.gameyfin.app.core.exceptions

import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        unmockkAll()
        clearAllMocks()
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

