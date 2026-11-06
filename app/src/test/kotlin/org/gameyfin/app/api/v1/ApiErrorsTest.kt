package org.gameyfin.app.api.v1

import com.vaadin.hilla.exception.EndpointException
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals

class ApiErrorsTest {
    private val errors = ApiErrors()

    private fun assertProblem(error: Exception, status: Int, code: String, detail: String? = null) {
        val response = errors.handle(error)
        assertEquals(status, response.statusCode.value())
        assertEquals(status, response.body!!.status)
        assertEquals(code, response.body!!.properties!!["code"])
        detail?.let { assertEquals(it, response.body!!.detail) }
    }

    @Test
    fun `security lookup and conflict errors keep their messages`() {
        assertProblem(AccessDeniedException("Missing permission"), 403, "forbidden", "Missing permission")
        assertProblem(NoSuchElementException("Game not found"), 404, "not_found", "Game not found")
        assertProblem(IllegalStateException("Fulfilled"), 409, "conflict", "Fulfilled")
        assertProblem(EndpointException("Endpoint conflict"), 409, "conflict", "Endpoint conflict")
        assertProblem(
            DataIntegrityViolationException("duplicate key secret"), 409, "conflict",
            "The change conflicts with an existing resource"
        )
    }

    @Test
    fun `invalid requests map to bad request`() {
        assertProblem(IllegalArgumentException("Unsupported sort"), 400, "invalid_request", "Unsupported sort")
        assertProblem(IllegalArgumentException(), 400, "invalid_request", "Bad Request")
        assertProblem(
            HttpMessageNotReadableException("raw parser detail", MockHttpInputMessage(ByteArray(0))),
            400, "invalid_request", "Invalid request body"
        )
        val parameter = MethodParameter(String::class.java.getMethod("substring", Int::class.java), 0)
        assertProblem(
            MethodArgumentTypeMismatchException("x", Int::class.java, "page", parameter, null),
            400, "invalid_request"
        )
    }

    @Test
    fun `validation errors list field messages`() {
        val binding = BeanPropertyBindingResult(Any(), "input")
        binding.addError(FieldError("input", "name", "must not be blank"))
        binding.addError(FieldError("input", "directories", null, false, null, null, null))
        val parameter = MethodParameter(String::class.java.getMethod("substring", Int::class.java), 0)

        val response = errors.handle(MethodArgumentNotValidException(parameter, binding))

        assertEquals(400, response.statusCode.value())
        assertEquals("Request validation failed", response.body!!.detail)
        assertEquals(
            listOf(
                mapOf("field" to "name", "message" to "must not be blank"),
                mapOf("field" to "directories", "message" to "Invalid value")
            ),
            response.body!!.properties!!["errors"]
        )
    }

    @Test
    fun `framework status errors keep their status`() {
        assertProblem(
            ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Slow down"), 429, "request_failed", "Slow down"
        )
        assertProblem(ResponseStatusException(HttpStatus.NOT_FOUND), 404, "not_found", "Not Found")
        assertProblem(HttpRequestMethodNotSupportedException("TRACE"), 405, "request_failed")
    }

    @Test
    fun `unexpected errors do not leak details`() {
        assertProblem(RuntimeException("database password"), 500, "request_failed", "The request could not be completed")
    }
}
