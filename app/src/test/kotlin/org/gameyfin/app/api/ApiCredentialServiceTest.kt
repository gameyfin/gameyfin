package org.gameyfin.app.api

import io.mockk.*
import org.gameyfin.app.api.security.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.RoleHierarchyConfig
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.Optional
import kotlin.test.*

class ApiCredentialServiceTest {
    private val repository = mockk<ApiCredentialRepository>()
    private val users = mockk<UserRepository>()
    private val userService = mockk<UserService>()
    private val service = ApiCredentialService(repository, users, userService, RoleHierarchyConfig().roleHierarchy())
    private val owner = User(id = 7, username = "owner", email = "owner@example.test", enabled = true, roles = listOf(Role.ADMIN))

    @AfterEach fun clear() = SecurityContextHolder.clearContext()

    private fun session(admin: Boolean = true) {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken("owner", null,
            listOf(SimpleGrantedAuthority(if (admin) Role.Names.ADMIN else Role.Names.USER)))
        every { users.findById(7) } returns Optional.of(owner)
        every { userService.getUserInfo() } returns ExtendedUserInfoDto(7, "owner", false, owner.email, true, true, false, roles = owner.roles)
        every { repository.save(any()) } answers { firstArg() }
    }

    private fun created(serviceKey: Boolean = false): Pair<CreatedCredential, ApiCredential> {
        session()
        val saved = slot<ApiCredential>()
        every { repository.save(capture(saved)) } answers { firstArg() }
        val created = service.create(serviceKey, CredentialInput("integration", scopes = if (serviceKey) setOf("games:read") else emptySet()))
        every { repository.findById(saved.captured.id) } returns Optional.of(saved.captured)
        return created to saved.captured
    }

    @Test fun `secrets are random hashed and revealed only on creation`() {
        val (created, stored) = created()
        assertTrue(created.secret.startsWith("gf_pat_"))
        assertEquals(64, stored.secretDigest.length)
        assertNotEquals(created.secret.substringAfter('.'), stored.secretDigest)
        assertEquals(ApiCredentialService.digest(created.secret.substringAfter('.')), stored.secretDigest)
        assertFalse(created.toString().contains(created.secret))
        assertFalse(created.credential.toString().contains(created.secret.substringAfter('.')))
        assertNotEquals(created.secret, service.create(false, CredentialInput("another")).secret)
    }

    @Test fun `personal tokens follow renamed accounts current roles and disablement`() {
        val (created, _) = created()
        assertTrue(service.authenticate(created.secret).authorities.any { it.authority == Role.Names.ADMIN })
        owner.username = "renamed"
        owner.roles = listOf(Role.USER)
        val auth = service.authenticate(created.secret)
        assertEquals("renamed", auth.name)
        assertEquals(7, auth.actor.userId)
        assertFalse(auth.authorities.any { it.authority == Role.Names.ADMIN })
        owner.enabled = false
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret) }
    }

    @Test fun `service keys do not inherit creators roles and survive creator disablement`() {
        val (created, stored) = created(true)
        assertNull(stored.owner)
        owner.enabled = false
        owner.roles = emptyList()
        val auth = service.authenticate(created.secret)
        assertNull(auth.actor.userId)
        assertEquals(listOf("SCOPE_games:read"), auth.authorities.map { it.authority })
        stored.scopes = mutableSetOf("games:write")
        assertEquals(listOf("SCOPE_games:write"), service.authenticate(created.secret).authorities.map { it.authority })
    }

    @Test fun `revoked expired unknown and malformed keys are rejected`() {
        val (created, stored) = created()
        stored.expiresAt = Instant.now()
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret) }
        stored.expiresAt = null
        stored.revokedAt = Instant.now()
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret) }
        stored.revokedAt = null
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret.dropLast(1) + "!") }
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret.replace("gf_pat_", "gf_key_")) }
        assertFailsWith<BadCredentialsException> { service.authenticate("short") }
        every { repository.findById(stored.id) } returns Optional.empty()
        assertFailsWith<BadCredentialsException> { service.authenticate(created.secret) }
    }

    @Test fun `only authorized browser sessions can manage credentials`() {
        session(false)
        assertFailsWith<AccessDeniedException> { service.create(true, CredentialInput("service", scopes = setOf("games:read"))) }
        val (created, _) = created()
        SecurityContextHolder.getContext().authentication = service.authenticate(created.secret)
        assertFailsWith<AccessDeniedException> { service.create(false, CredentialInput("child")) }
        assertFailsWith<AccessDeniedException> { service.list(false) }
    }

    @Test fun `users cannot manage other users tokens or service keys`() {
        val (_, stored) = created(true)
        assertFailsWith<AccessDeniedException> { service.revoke(stored.id, false) }
        assertFailsWith<AccessDeniedException> { service.update(stored.id, false, CredentialInput("stolen")) }
        val other = ApiCredential(kind = CredentialKind.PERSONAL, name = "other", secretDigest = "digest",
            owner = User(id = 9, username = "other", email = "other@example.test"))
        every { repository.findById(other.id) } returns Optional.of(other)
        assertFailsWith<AccessDeniedException> { service.revoke(other.id, false) }
    }

    @Test fun `scope and expiration inputs are validated`() {
        session()
        for (input in listOf(CredentialInput(""), CredentialInput("key", Instant.now().minusSeconds(1)), CredentialInput("key", scopes = setOf("games:read")))) {
            assertFailsWith<IllegalArgumentException> { service.create(false, input) }
        }
        assertFailsWith<IllegalArgumentException> { service.create(true, CredentialInput("key")) }
        assertFailsWith<IllegalArgumentException> { service.create(true, CredentialInput("key", scopes = setOf("*"))) }
    }

    @Test fun `access checks never upgrade service scopes to administrator privileges`() {
        val (created, _) = created(true)
        SecurityContextHolder.getContext().authentication = service.authenticate(created.secret)
        val access = ApiAccess()
        access.require("games:read")
        assertFailsWith<AccessDeniedException> { access.require("games:write", true) }
        assertFailsWith<AccessDeniedException> { access.require("games:delete", true) }
        assertFailsWith<AccessDeniedException> { access.personal() }
    }

    @Test fun `regeneration replaces the secret and preserves credential settings`() {
        for (serviceKey in listOf(false, true)) {
            val (original, stored) = created(serviceKey)
            stored.expiresAt = Instant.now().plusSeconds(86400)
            val expiresAt = stored.expiresAt
            val createdAt = stored.createdAt
            val regenerated = service.regenerate(stored.id, serviceKey)
            assertNotEquals(original.secret, regenerated.secret)
            assertEquals(original.credential.id, regenerated.credential.id)
            assertEquals(original.credential.name, regenerated.credential.name)
            assertEquals(original.credential.scopes, regenerated.credential.scopes)
            assertEquals(expiresAt, regenerated.credential.expiresAt)
            assertEquals(createdAt, regenerated.credential.createdAt)
            assertEquals(owner, stored.creator)
            assertFailsWith<BadCredentialsException> { service.authenticate(original.secret) }
            assertEquals(stored.id, service.authenticate(regenerated.secret).actor.credentialId)
            stored.revokedAt = Instant.now()
            assertFailsWith<IllegalArgumentException> { service.regenerate(stored.id, serviceKey) }
        }
    }

    @Test fun `only revoked credentials can be permanently deleted`() {
        for (serviceKey in listOf(false, true)) {
            val (_, stored) = created(serviceKey)
            assertFailsWith<IllegalArgumentException> { service.delete(stored.id, serviceKey) }
            stored.expiresAt = Instant.now().minusSeconds(1)
            assertFailsWith<IllegalArgumentException> { service.delete(stored.id, serviceKey) }
            verify(exactly = 0) { repository.delete(stored) }
            stored.revokedAt = Instant.now()
            every { repository.delete(stored) } just Runs
            service.delete(stored.id, serviceKey)
            verify(exactly = 1) { repository.delete(stored) }
        }
    }

    @Test fun `deletion and regeneration enforce ownership kind and administrator access`() {
        val (_, key) = created(true)
        assertFailsWith<AccessDeniedException> { service.delete(key.id, false) }
        assertFailsWith<AccessDeniedException> { service.regenerate(key.id, false) }
        session(false)
        assertFailsWith<AccessDeniedException> { service.delete(key.id, true) }
        assertFailsWith<AccessDeniedException> { service.regenerate(key.id, true) }
        val other = ApiCredential(kind = CredentialKind.PERSONAL, name = "other", secretDigest = "digest",
            owner = User(id = 9, username = "other", email = "other@example.test"))
        every { repository.findById(other.id) } returns Optional.of(other)
        assertFailsWith<AccessDeniedException> { service.delete(other.id, false) }
        assertFailsWith<AccessDeniedException> { service.regenerate(other.id, false) }
        session()
        val regenerated = service.regenerate(key.id, true)
        SecurityContextHolder.getContext().authentication = service.authenticate(regenerated.secret)
        assertFailsWith<AccessDeniedException> { service.delete(key.id, true) }
        assertFailsWith<AccessDeniedException> { service.regenerate(key.id, true) }
    }
}
