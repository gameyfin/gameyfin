package org.gameyfin.app.core.annotations

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class DynamicAccessInterceptor(
    private val config: ConfigService
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val handlerMethod = (handler as? HandlerMethod) ?: return true
        val method = handlerMethod.method
        val clazz = handlerMethod.beanType

        val hasDynamicPublicAccess =
            method.isAnnotationPresent(DynamicPublicAccess::class.java) ||
                    clazz.isAnnotationPresent(DynamicPublicAccess::class.java)

        if (hasDynamicPublicAccess) {
            if (request.userPrincipal != null || config.get(ConfigProperties.Libraries.AllowPublicAccess) == true) {
                return true
            }
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return false
        }

        return true
    }
}
