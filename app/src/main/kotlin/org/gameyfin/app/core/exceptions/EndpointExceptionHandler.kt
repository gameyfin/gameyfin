package org.gameyfin.app.core.exceptions

import com.vaadin.hilla.exception.EndpointException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

/**
 * Aspect that intercepts all Vaadin Hilla endpoint method calls.
 * Catches all exceptions thrown from endpoint methods, logs them with full stack trace,
 * and re-throws them as EndpointException to be displayed nicely in the frontend.
 */
@Aspect
@Component
class EndpointExceptionHandler {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Around("@within(com.vaadin.hilla.Endpoint)")
    @Throws(Throwable::class)
    fun handleEndpointException(joinPoint: ProceedingJoinPoint): Any? {
        return try {
            joinPoint.proceed()
        } catch (ex: EndpointException) {
            // If it's already an EndpointException, just log and re-throw
            log.error(ex) { "Endpoint exception: ${ex.message}" }
            throw ex
        } catch (ex: Exception) {
            // Log the original exception with full stack trace
            log.error(ex) { "Exception in endpoint method ${joinPoint.signature.declaringType.simpleName}.${joinPoint.signature.name}: ${ex.message}" }

            // Re-throw as EndpointException with the original message but no stack trace
            throw EndpointException(ex.message ?: "An error occurred")
        }
    }
}

