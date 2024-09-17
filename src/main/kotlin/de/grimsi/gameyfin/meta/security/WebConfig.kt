package de.grimsi.gameyfin.meta.security

import de.grimsi.gameyfin.meta.annotations.DynamicAccessInterceptor
import de.grimsi.gameyfin.meta.development.DelayInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    private val dynamicAccessInterceptor: DynamicAccessInterceptor
) : WebMvcConfigurer {

    @Autowired(required = false)
    private var delayInterceptor: DelayInterceptor? = null

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(dynamicAccessInterceptor)
        delayInterceptor?.let { registry.addInterceptor(it) }
    }
}
