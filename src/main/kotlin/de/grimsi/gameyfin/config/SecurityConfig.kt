package de.grimsi.gameyfin.config

import com.vaadin.flow.spring.security.VaadinWebSecurity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.util.matcher.AntPathRequestMatcher


@EnableWebSecurity
@Configuration
class SecurityConfig : VaadinWebSecurity() {

    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        // Configure your static resources with public access before calling super.configure(HttpSecurity) as it adds final anyRequest matcher
        http.authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
            auth.requestMatchers("/setup").permitAll()
                .requestMatchers("/public/**").permitAll()
        }

        super.configure(http)
        setLoginView(http, "/login")
    }

    @Throws(Exception::class)
    public override fun configure(web: WebSecurity) {
        super.configure(web)
        web.ignoring().requestMatchers(AntPathRequestMatcher("/images/**"))
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}