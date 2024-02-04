package de.grimsi.gameyfin.security

import com.vaadin.flow.spring.security.VaadinWebSecurity
import de.grimsi.gameyfin.ui.views.LoginView
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.provisioning.UserDetailsManager
import org.springframework.security.web.util.matcher.AntPathRequestMatcher


@EnableWebSecurity
@Configuration
class SecurityConfiguration : VaadinWebSecurity() {
    @Throws(Exception::class)
    override fun configure(http: HttpSecurity) {
        // Configure your static resources with public access before calling super.configure(HttpSecurity) as it adds final anyRequest matcher
        http.authorizeHttpRequests { auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry ->
            auth.requestMatchers(AntPathRequestMatcher("/public/**")).permitAll()
        }

        // Configure your static resources with public access before calling
        // super.configure(HttpSecurity) as it adds final anyRequest matcher

        super.configure(http)

        // This is important to register your login view to the navigation access control mechanism:
        setLoginView(http, LoginView::class.java)
    }

    @Throws(Exception::class)
    public override fun configure(web: WebSecurity) {
        super.configure(web)
    }

    /**
     * TODO: Just for testing
     */
    @Bean
    fun userDetailsService(): UserDetailsManager {
        val user: UserDetails =
                User.withUsername("user")
                        .password("{noop}user")
                        .roles("USER")
                        .build()
        val admin: UserDetails =
                User.withUsername("admin")
                        .password("{noop}admin")
                        .roles("ADMIN")
                        .build()
        return InMemoryUserDetailsManager(user, admin)
    }
}