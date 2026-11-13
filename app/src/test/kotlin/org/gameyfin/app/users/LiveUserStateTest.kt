package org.gameyfin.app.users

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.LiveSubscriptionSecurity
import org.gameyfin.app.core.security.LiveUserAuthentication
import org.gameyfin.app.core.security.LiveUserSecurityFilter
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserEvent
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import reactor.test.StepVerifier
import java.util.*
import kotlin.test.*

class LiveUserStateTest {
    private val hierarchy = RoleHierarchyImpl.fromHierarchy("ROLE_SUPERADMIN > ROLE_ADMIN\nROLE_ADMIN > ROLE_USER")
    private fun dto(id: Long = 901, roles: List<Role> = listOf(Role.USER), enabled: Boolean = true) =
        ExtendedUserInfoDto(id, "user$id", false, "user$id@example.com", true, enabled, false, roles = roles)

    private fun authenticate(user: ExtendedUserInfoDto): LiveUserAuthentication {
        val original = UsernamePasswordAuthenticationToken.authenticated(
            user.username,
            "",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        return LiveUserAuthentication(original, user, hierarchy)
    }

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `all sessions observe promotions and demotions without logging in again`() {
        val first = authenticate(dto())
        val second = authenticate(dto())
        val events = UserEvents()
        events.committed(UserStateChanged(UserEvent(901, dto(roles = listOf(Role.ADMIN)))))
        for (session in listOf(first, second)) {
            assertTrue(session.isAuthenticated)
            assertEquals(setOf("ROLE_ADMIN", "ROLE_USER"), session.authorities.map { it.authority }.toSet())
        }
        events.committed(UserStateChanged(UserEvent(901, dto())))
        for (session in listOf(first, second)) assertEquals(
            listOf("ROLE_USER"),
            session.authorities.map { it.authority })
    }

    @Test
    fun `disabled and deleted users lose authentication`() {
        val disabled = authenticate(dto(902))
        val deleted = authenticate(dto(903))
        UserEvents().committed(UserStateChanged(UserEvent(902, dto(902, enabled = false))))
        UserEvents().committed(UserStateChanged(UserEvent(903)))
        assertFalse(disabled.isAuthenticated)
        assertFalse(deleted.isAuthenticated)
        assertTrue(disabled.authorities.isEmpty())
        assertTrue(deleted.authorities.isEmpty())
        UserEvents().committed(UserStateChanged(UserEvent(902, dto(902))))
        assertFalse(disabled.isAuthenticated, "Re-enabling an account must not revive revoked sessions")
    }

    @Test
    fun `HTTP authorization observes both added and removed admin access`() {
        val auth = authenticate(dto(910))
        SecurityContextHolder.getContext().authentication = auth
        val manager =
            org.springframework.security.authorization.AuthorityAuthorizationManager.hasRole<jakarta.servlet.http.HttpServletRequest>(
                "ADMIN"
            )
        val filter = org.springframework.security.web.access.intercept.AuthorizationFilter(manager)
        fun request() = filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse()) { _, response ->
            (response as jakarta.servlet.http.HttpServletResponse).status = 204
        }
        assertFailsWith<org.springframework.security.access.AccessDeniedException> { request() }
        UserEvents().committed(UserStateChanged(UserEvent(910, dto(910, listOf(Role.ADMIN)))))
        request()
        UserEvents().committed(UserStateChanged(UserEvent(910, dto(910))))
        assertFailsWith<org.springframework.security.access.AccessDeniedException> { request() }
    }

    @Test
    fun `OIDC identity and non role authorities survive role refresh`() {
        val principal = mockk<OidcUser>()
        val original = UsernamePasswordAuthenticationToken.authenticated(
            principal, "token", listOf(
                SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("SCOPE_openid")
            )
        )
        val auth = LiveUserAuthentication(original, dto(), hierarchy)
        assertSame(principal, auth.principal)
        assertEquals("token", auth.credentials)
        assertEquals(setOf("ROLE_USER", "SCOPE_openid"), auth.authorities.map { it.authority }.toSet())
    }

    @Test
    fun `HTTP filter keeps stable identity after username change`() {
        val users = mockk<UserRepository>()
        val user =
            User(id = 904, username = "before", email = "a@example.com", enabled = true, roles = listOf(Role.USER))
        every { users.findByUsername("before") } returns user
        every { users.findById(904) } returns Optional.of(user)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated("before", "", emptyList())
        val filter = LiveUserSecurityFilter(users, hierarchy)
        val request = MockHttpServletRequest().apply { session }
        filter.doFilter(request, MockHttpServletResponse()) { _, _ -> }
        val auth = SecurityContextHolder.getContext().authentication as LiveUserAuthentication
        user.username = "after"
        filter.doFilter(MockHttpServletRequest(), MockHttpServletResponse()) { _, _ -> }
        assertSame(auth, SecurityContextHolder.getContext().authentication)
        assertEquals("after", auth.name)
        assertEquals(904L, auth.userId)
    }

    @Test
    fun `personal stream cannot receive other users and delivers final deletion`() {
        val events = UserEvents()
        StepVerifier.create(events.subscribeCurrentUser(905))
            .then { events.committed(UserStateChanged(UserEvent(906, dto(906)))) }
            .then { events.committed(UserStateChanged(UserEvent(905, dto(905)))) }
            .assertNext { assertEquals(905L, it.single().id) }
            .then { events.committed(UserStateChanged(UserEvent(905))) }
            .assertNext { assertNull(it.single().user) }
            .verifyComplete()
    }

    @Test
    fun `admin stream closes on demotion before delivering user data`() {
        val events = UserEvents()
        SecurityContextHolder.getContext().authentication = authenticate(dto(907, listOf(Role.ADMIN)))
        val factory = AspectJProxyFactory(UserEndpoint(mockk(), mockk(), events))
        factory.addAspect(LiveSubscriptionSecurity(events, mockk()))
        val endpoint = factory.getProxy<UserEndpoint>()
        StepVerifier.create(endpoint.subscribe())
            .then { events.committed(UserStateChanged(UserEvent(908, dto(908)))) }
            .expectNextCount(1)
            .then { events.committed(UserStateChanged(UserEvent(907, dto(907)))) }
            .verifyComplete()
    }

    @Test
    fun `transaction rollback publishes nothing and commit publishes once`() {
        AnnotationConfigApplicationContext(TransactionConfig::class.java).use { context ->
            val events = context.getBean(UserEvents::class.java)
            val transaction = TransactionTemplate(context.getBean(AbstractPlatformTransactionManager::class.java))
            val received = mutableListOf<UserEvent>()
            val subscription = events.changes().subscribe { received.add(it) }
            transaction.executeWithoutResult { status ->
                context.publishEvent(UserStateChanged(UserEvent(909, dto(909))))
                assertTrue(received.isEmpty())
                status.setRollbackOnly()
            }
            assertTrue(received.isEmpty())
            transaction.executeWithoutResult {
                context.publishEvent(UserStateChanged(UserEvent(909, dto(909))))
                assertTrue(received.isEmpty())
            }
            assertEquals(1, received.size)
            subscription.dispose()
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class TransactionConfig {
        @Bean
        fun events() = UserEvents()

        @Bean
        fun transactionManager() = object : AbstractPlatformTransactionManager() {
            override fun doGetTransaction(): Any = Any()
            override fun doBegin(transaction: Any, definition: org.springframework.transaction.TransactionDefinition) {}
            override fun doCommit(status: DefaultTransactionStatus) {}
            override fun doRollback(status: DefaultTransactionStatus) {}
        }
    }
}
