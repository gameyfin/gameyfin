package org.gameyfin.app.core.security

import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.users.UserEvents
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/** Enforce dynamic endpoint access and revoke protected streams on user changes. */
@Aspect
@Component
class LiveSubscriptionSecurity(private val events: UserEvents, private val config: ConfigService) {
    @Around("execution(* org.gameyfin.app..*Endpoint.*(..))")
    fun guard(call: ProceedingJoinPoint): Any? {
        val auth = getCurrentAuth()
        val method = (call.signature as MethodSignature).method
        // The personal stream must deliver the final disabled/deleted notification.
        if (method.name == "subscribeCurrentUser" && method.declaringClass.simpleName == "UserEndpoint") return call.proceed()
        val roles = AnnotatedElementUtils.findMergedAnnotation(method, RolesAllowed::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(call.target.javaClass, RolesAllowed::class.java)
        val authenticated = roles != null || AnnotatedElementUtils.hasAnnotation(method, PermitAll::class.java) ||
                AnnotatedElementUtils.hasAnnotation(call.target.javaClass, PermitAll::class.java)
        val dynamic = AnnotatedElementUtils.hasAnnotation(method, DynamicPublicAccess::class.java) ||
                AnnotatedElementUtils.hasAnnotation(call.target.javaClass, DynamicPublicAccess::class.java)
        val loggedIn = auth?.isAuthenticated == true && !AuthenticationTrustResolverImpl().isAnonymous(auth)
        if (dynamic && !loggedIn && config.get(ConfigProperties.Security.AllowPublicAccess) != true) {
            throw AccessDeniedException("Login required")
        }
        val result = call.proceed()
        if (result !is Flux<*>) return result
        if (!authenticated && !dynamic) return result
        fun allowed(): Boolean {
            return !authenticated && dynamic && config.get(ConfigProperties.Security.AllowPublicAccess) == true || loggedIn &&
                    (roles == null || auth.authorities.any { it.authority in roles.value })
        }
        return result.takeUntilOther(events.changes().filter { !allowed() })
            .takeWhile { allowed() }
    }
}
