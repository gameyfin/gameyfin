package de.grimsi.gameyfin.meta

import com.vaadin.flow.spring.security.VaadinWebSecurity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val environment: Environment
) : VaadinWebSecurity() {

    @Bean
    fun sessionRegistry(): SessionRegistry {
        return SessionRegistryImpl()
    }

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        // Configure your static resources with public access before calling super.configure(HttpSecurity) as it adds final anyRequest matcher
        http.authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
            auth.requestMatchers("/setup").permitAll()
                .requestMatchers("/public/**").permitAll()
        }

        http.sessionManagement { sessionManagement ->
            sessionManagement
                .maximumSessions(3)
                .sessionRegistry(sessionRegistry())
        }

        super.configure(http)
        setLoginView(http, "/login")
    }

    @Throws(Exception::class)
    public override fun configure(web: WebSecurity) {
        super.configure(web)
        web.ignoring().requestMatchers("/images/**")

        if ("dev" in environment.activeProfiles) {
            web.ignoring().requestMatchers("/h2-console/**")
        }
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}