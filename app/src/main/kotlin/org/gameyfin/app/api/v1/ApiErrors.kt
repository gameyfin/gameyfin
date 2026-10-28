package org.gameyfin.app.api.v1

import com.vaadin.hilla.exception.EndpointException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(basePackages = ["org.gameyfin.app.api.v1"])
class ApiErrors {
    @ExceptionHandler(Exception::class)
    fun handle(error: Exception): ResponseEntity<ProblemDetail> {
        val status = when (error) {
            is AccessDeniedException -> HttpStatus.FORBIDDEN
            is NoSuchElementException -> HttpStatus.NOT_FOUND
            is DataIntegrityViolationException, is IllegalStateException, is EndpointException -> HttpStatus.CONFLICT
            is IllegalArgumentException, is HttpMessageNotReadableException,
            is MethodArgumentNotValidException, is MethodArgumentTypeMismatchException -> HttpStatus.BAD_REQUEST
            is ResponseStatusException -> HttpStatus.valueOf(error.statusCode.value())
            is org.springframework.web.ErrorResponse -> HttpStatus.valueOf(error.statusCode.value())
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
        val code = when (status.value()) { 400 -> "invalid_request"; 403 -> "forbidden"; 404 -> "not_found"; 409 -> "conflict"; else -> "request_failed" }
        val detail = when (error) {
            is DataIntegrityViolationException -> "The change conflicts with an existing resource"
            is HttpMessageNotReadableException -> "Invalid request body"
            is MethodArgumentNotValidException -> "Request validation failed"
            is ResponseStatusException -> error.reason ?: status.reasonPhrase
            else -> if (status.is5xxServerError) "The request could not be completed" else error.message ?: status.reasonPhrase
        }
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.setProperty("code", code)
        if (error is MethodArgumentNotValidException) problem.setProperty("errors", error.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "message" to (it.defaultMessage ?: "Invalid value"))
        })
        return ResponseEntity.status(status).body(problem)
    }
}
