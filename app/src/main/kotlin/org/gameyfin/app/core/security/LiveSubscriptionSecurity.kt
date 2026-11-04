package org.gameyfin.app.core.security

import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.gameyfin.app.users.UserEvents
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux

/** Hilla checks access at subscription time only. Revoke protected streams on user changes. */
@Aspect
@Component
class LiveSubscriptionSecurity(private val events: UserEvents, private val config: ConfigService) {
    @Around("execution(reactor.core.publisher.Flux org.gameyfin.app..*Endpoint.*(..))")
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
        val result = call.proceed() as Flux<*>
        if (!authenticated && !dynamic) return result
        fun allowed(): Boolean {
            if (!authenticated && dynamic && config.get(ConfigProperties.Security.AllowPublicAccess) == true) return true
            return auth?.isAuthenticated == true &&
                (roles == null || auth.authorities.any { it.authority in roles.value })
        }
        return result.takeUntilOther(events.changes().filter { !allowed() })
            .takeWhile { allowed() }
    }
}
