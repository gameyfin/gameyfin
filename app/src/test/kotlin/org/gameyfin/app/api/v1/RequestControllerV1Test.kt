package org.gameyfin.app.api.v1

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.controller.RequestControllerV1
import org.gameyfin.app.api.v1.dto.CreateRequestV1
import org.gameyfin.app.api.v1.dto.RequestStatusV1
import org.gameyfin.app.requests.GameRequestRepository
import org.gameyfin.app.requests.GameRequestService
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestControllerV1Test {
    private val access = allowingAccess(personalPrincipal(7))
    private val repository = mockk<GameRequestRepository>(relaxed = true)
    private val service = mockk<GameRequestService>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val controller = RequestControllerV1(access, repository, service, users)

    private val voter = User(id = 7, username = "owner", email = "o@example.test")
    private val other = User(id = 8, username = "other", email = "x@example.test")

    private fun entry(
        status: GameRequestStatus = GameRequestStatus.PENDING,
        requester: User? = voter,
        voters: MutableSet<User> = mutableSetOf(voter)
    ) = GameRequest(
        id = 1, title = "Title", release = Instant.parse("2020-01-01T00:00:00Z"),
        platform = Platform.entries.first(), status = status, requester = requester, voters = voters,
        linkedGameId = 99
    )

    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `requests are listed and fetched with personal vote state`() {
        val request = entry()
        every { repository.findAll(any<PageRequest>()) } returns PageImpl(listOf(request), PageRequest.of(0, 10), 1)
        every { repository.findById(1) } returns Optional.of(request)

        val item = controller.list(0, 10, "title").items.single()
        assertEquals("1", item.id)
        assertEquals("7", item.requesterId)
        assertEquals(1, item.voteCount)
        assertEquals(true, item.voted)
        assertEquals("99", item.linkedGameId)
        assertEquals(java.time.LocalDate.of(2020, 1, 1), item.release)

        assertEquals("PENDING", controller.get("1").status)
        every { repository.findById(2) } returns Optional.empty()
        assertFailsWith<NoSuchElementException> { controller.get("2") }
    }

    @Test
    fun `service keys see no vote state`() {
        val serviceAccess = allowingAccess(servicePrincipal())
        val request = entry(requester = null)
        every { repository.findById(1) } returns Optional.of(request)

        val view = RequestControllerV1(serviceAccess, repository, service, users).get("1")

        assertNull(view.voted)
        assertNull(view.requesterId)
    }

    @Test
    fun `requests are created for personal tokens`() {
        val dto = slot<GameRequestCreationDto>()
        every { service.createReturningId(capture(dto)) } returns 1
        every { repository.findById(1) } returns Optional.of(entry())

        val response = controller.create(
            CreateRequestV1(
                "Title",
                Platform.entries.first().name,
                java.time.LocalDate.of(2020, 1, 1)
            )
        )

        assertEquals(201, response.statusCode.value())
        assertEquals("/v1/requests/1", response.headers.location.toString())
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), dto.captured.release)
        verify { access.personal() }

        controller.create(CreateRequestV1("Title", Platform.entries.first().name))
        assertNull(dto.captured.release)
        assertFailsWith<IllegalArgumentException> { controller.create(CreateRequestV1("Title", "NOPE")) }
    }

    @Test
    fun `status changes are applied unless the request is fulfilled`() {
        every { repository.findById(1) } returns Optional.of(entry())

        assertEquals("PENDING", controller.status("1", RequestStatusV1("APPROVED")).status)
        verify { service.changeRequestStatus(1, GameRequestStatus.APPROVED) }
        verify { access.require(ApiScope.REQUESTS_WRITE) }

        every { repository.findById(1) } returns Optional.of(entry(GameRequestStatus.FULFILLED))
        assertFailsWith<IllegalStateException> { controller.status("1", RequestStatusV1("REJECTED")) }
        controller.status("1", RequestStatusV1("FULFILLED"))
        assertFailsWith<IllegalArgumentException> { controller.status("1", RequestStatusV1("UNKNOWN")) }
    }

    @Test
    fun `personal tokens delete only their own requests unless administrator`() {
        every { repository.findById(1) } returns Optional.of(entry(requester = voter))
        assertEquals(204, controller.delete("1").statusCode.value())
        verify { service.deleteRequest(1) }

        every { repository.findById(1) } returns Optional.of(entry(requester = other))
        assertFailsWith<AccessDeniedException> { controller.delete("1") }

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "owner", null, listOf(SimpleGrantedAuthority("ROLE_ADMIN"))
        )
        assertEquals(204, controller.delete("1").statusCode.value())
    }

    @Test
    fun `service keys can delete any request`() {
        val serviceController = RequestControllerV1(allowingAccess(servicePrincipal()), repository, service, users)
        every { repository.findById(1) } returns Optional.of(entry(requester = other))

        assertEquals(204, serviceController.delete("1").statusCode.value())
    }

    @Test
    fun `votes are added and removed idempotently`() {
        val request = entry(voters = mutableSetOf())
        every { repository.findForVote(1) } returns request
        every { users.findById(7) } returns Optional.of(voter)

        repeat(2) { assertEquals(204, controller.vote("1").statusCode.value()) }
        assertEquals(listOf(7L), request.voters.map { it.id })

        repeat(2) { assertEquals(204, controller.unvote("1").statusCode.value()) }
        assertTrue(request.voters.isEmpty())
    }

    @Test
    fun `votes fail for missing requests or accounts`() {
        every { repository.findForVote(2) } returns null
        assertFailsWith<NoSuchElementException> { controller.vote("2") }

        every { repository.findForVote(1) } returns entry()
        every { users.findById(7) } returns Optional.empty()
        assertFailsWith<AccessDeniedException> { controller.vote("1") }
    }
}
