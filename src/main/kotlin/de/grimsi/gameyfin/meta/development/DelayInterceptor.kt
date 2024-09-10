package de.grimsi.gameyfin.meta.development

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor


@Component
@Profile("development")
class DelayInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        Thread.sleep(2000)
        return true
    }

}