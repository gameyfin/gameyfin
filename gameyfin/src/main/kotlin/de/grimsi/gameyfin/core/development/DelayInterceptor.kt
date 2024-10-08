package de.grimsi.gameyfin.core.development

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor


@Component
@Profile("delay")
class DelayInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.requestURI.startsWith("/connect")) Thread.sleep(2000)
        return true
    }

}