package de.grimsi.gameyfin.meta

import de.grimsi.gameyfin.meta.annotations.DynamicAccessInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(val dynamicAccessInterceptor: DynamicAccessInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(dynamicAccessInterceptor)
    }
}
