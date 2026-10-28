package org.gameyfin.app.api

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.api.security.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.config.annotation.EnableWebMvc

class ApiSecurityTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import(ApiSecurityConfig::class, Probe::class)
    class Config {
        @Bean fun config(): ConfigService = mockk<ConfigService>().also {
            every { it.get(ConfigProperties.Security.SwaggerUiEnabled) } returns true
        }

        @Bean fun credentials(): ApiCredentialService = mockk<ApiCredentialService>().also {
            every { it.authenticate(any()) } throws BadCredentialsException("Invalid API key")
            every { it.authenticate("valid") } returns ApiAuthentication(ApiPrincipal("id", "key", CredentialKind.SERVICE, null, "api-key:id"), listOf(SimpleGrantedAuthority("SCOPE_games:read")))
        }
    }
    @RestController class Probe {
        @GetMapping("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
            "/v3/api-docs", "/v3/api-docs/v1", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml", "/v3/api-docs.yaml/v1")
        fun documentation() = "API documentation"

        @GetMapping("/v1/probe") fun probe() = mapOf("status" to "ok")
        @GetMapping("/v1/stream") fun stream() = org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody {
            it.write("game archive".toByteArray())
        }
    }

    @Test fun `documentation can be disabled and re-enabled without restarting or affecting the API`() {
        WebApplicationContextRunner().withUserConfiguration(Config::class.java).run { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context.sourceApplicationContext).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            val config = context.getBean(ConfigService::class.java)
            val paths = listOf("/swagger-ui.html", "/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
                "/v3/api-docs", "/v3/api-docs/v1", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml", "/v3/api-docs.yaml/v1")

            paths.forEach { mvc.perform(get(it)).andExpect(status().isOk) }

            every { config.get(ConfigProperties.Security.SwaggerUiEnabled) } returns false
            paths.forEach {
                mvc.perform(get(it)).andExpect(status().isNotFound)
                    .andExpect(header().string("Cache-Control", "no-store"))
            }
            mvc.perform(get("/v1/probe").header("X-API-Key", "valid")).andExpect(status().isOk)
            mvc.perform(get("/v1/probe")).andExpect(status().isUnauthorized)

            every { config.get(ConfigProperties.Security.SwaggerUiEnabled) } returns true
            paths.forEach { mvc.perform(get(it)).andExpect(status().isOk) }
        }
    }

    @Test fun `only the API key header authenticates and no session is created`() {
        WebApplicationContextRunner().withUserConfiguration(Config::class.java).run { context ->
            val mvc = MockMvcBuilders.webAppContextSetup(context.sourceApplicationContext).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
            mvc.perform(get("/v1/probe")).andExpect(status().isUnauthorized).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            mvc.perform(get("/v1/probe").header("Authorization", "Bearer valid")).andExpect(status().isUnauthorized)
            mvc.perform(get("/v1/probe").param("api_key", "valid")).andExpect(status().isUnauthorized)
            mvc.perform(get("/v1/probe").header("X-API-Key", "invalid")).andExpect(status().isUnauthorized)
            val result = mvc.perform(get("/v1/probe").header("X-API-Key", "valid"))
                .andExpect(status().isOk).andExpect(cookie().doesNotExist("JSESSIONID")).andReturn()
            kotlin.test.assertNull(result.request.getSession(false))
            val streaming = mvc.perform(get("/v1/stream").header("X-API-Key", "valid"))
                .andExpect(request().asyncStarted()).andReturn()
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(streaming))
                .andExpect(status().isOk).andExpect(content().string("game archive"))
            kotlin.test.assertNull(streaming.request.getSession(false))
        }
    }
}
