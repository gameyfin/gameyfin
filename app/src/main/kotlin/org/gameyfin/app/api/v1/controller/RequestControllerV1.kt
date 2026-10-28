package org.gameyfin.app.api.v1.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.security.CredentialKind
import org.gameyfin.app.api.v1.apiEnum
import org.gameyfin.app.api.v1.apiId
import org.gameyfin.app.api.v1.apiPage
import org.gameyfin.app.api.v1.dto.CreateRequestV1
import org.gameyfin.app.api.v1.dto.PageV1
import org.gameyfin.app.api.v1.dto.RequestStatusV1
import org.gameyfin.app.api.v1.dto.RequestV1
import org.gameyfin.app.api.v1.publicPage
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.requests.GameRequestRepository
import org.gameyfin.app.requests.GameRequestService
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.ZoneOffset

@Tag(name = "Requests", description = "Game requests, their status and votes.")
@RestController
@RequestMapping("/v1/requests")
class RequestControllerV1(
    private val access: ApiAccess, private val repository: GameRequestRepository,
    private val service: GameRequestService, private val users: UserRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(
        operationId = "listRequests",
        description = "Requires requests:read. sort: id, title, createdAt or updatedAt."
    )
    fun list(
        @Parameter(description = "Zero-based page index.") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size.") @RequestParam(defaultValue = "50") size: Int,
        @Parameter(description = "Sort field, optionally followed by ,asc or ,desc. Allowed fields: id, title, createdAt, updatedAt.")
        @RequestParam(defaultValue = "id") sort: String
    ): PageV1<RequestV1> {
        access.require(ApiScope.REQUESTS_READ)
        return repository.findAll(apiPage(page, size, sort, setOf("id", "title", "createdAt", "updatedAt")))
            .publicPage(::view)
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @Operation(operationId = "getRequest", description = "Requires requests:read.")
    fun get(@Parameter(description = "Request ID.") @PathVariable id: String): RequestV1 {
        access.require(ApiScope.REQUESTS_READ); return view(entity(id))
    }

    @PostMapping
    @Transactional
    @Operation(
        operationId = "createRequest",
        description = "Personal tokens only. Honors instance request settings and the owner's open-request limit."
    )
    @ApiResponse(responseCode = "201", description = "Created")
    fun create(@Valid @RequestBody input: CreateRequestV1): ResponseEntity<RequestV1> {
        access.personal()
        val id = service.createReturningId(
            GameRequestCreationDto(
                input.title,
                apiEnum<Platform>(input.platform),
                input.release?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
            )
        )
        repository.flush()
        return ResponseEntity.created(URI.create("/v1/requests/$id")).body(view(entity(id.toString())))
    }

    @PatchMapping("/{id}/status")
    @Transactional
    @Operation(
        operationId = "changeRequestStatus",
        description = "Requires requests:write or an administrator personal token. Fulfilled requests cannot be changed."
    )
    fun status(@Parameter(description = "Request ID.") @PathVariable id: String, @RequestBody input: RequestStatusV1): RequestV1 {
        access.require(ApiScope.REQUESTS_WRITE)
        val request = entity(id)
        val status = apiEnum<GameRequestStatus>(input.status)
        check(request.status != GameRequestStatus.FULFILLED || status == request.status) { "Fulfilled requests cannot be changed" }
        service.changeRequestStatus(request.id!!, status)
        repository.flush()
        return view(request)
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(
        operationId = "deleteRequest",
        description = "Requires requests:delete. Personal tokens may delete their owner's requests; administrators may delete any request."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun delete(@Parameter(description = "Request ID.") @PathVariable id: String): ResponseEntity<Void> {
        val actor = access.require(ApiScope.REQUESTS_DELETE)
        val request = entity(id)
        if (actor.kind == CredentialKind.PERSONAL && !isCurrentUserAdmin() && request.requester?.id != actor.userId) {
            throw AccessDeniedException("Only the requester or an administrator may delete this request")
        }
        service.deleteRequest(request.id!!)
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/{id}/vote")
    @Transactional
    @Operation(
        operationId = "voteForRequest",
        description = "Personal tokens only. Idempotently adds the owner's vote."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun vote(@Parameter(description = "Request ID.") @PathVariable id: String): ResponseEntity<Void> = setVote(id, true)

    @DeleteMapping("/{id}/vote")
    @Transactional
    @Operation(
        operationId = "removeRequestVote",
        description = "Personal tokens only. Idempotently removes the owner's vote."
    )
    @ApiResponse(responseCode = "204", description = "No content")
    fun unvote(@Parameter(description = "Request ID.") @PathVariable id: String): ResponseEntity<Void> = setVote(id, false)

    private fun setVote(id: String, present: Boolean): ResponseEntity<Void> {
        val actor = access.personal()
        val request = repository.findForVote(apiId(id)) ?: throw NoSuchElementException("Request not found")
        val user = users.findById(actor.userId!!).orElseThrow { AccessDeniedException("Account not found") }
        val voters = request.voters.toMutableSet()
        if (present && voters.none { it.id == user.id }) voters.add(user)
        if (!present) voters.removeIf { it.id == user.id }
        request.voters = voters
        return ResponseEntity.noContent().build()
    }

    private fun entity(id: String) =
        repository.findById(apiId(id)).orElseThrow { NoSuchElementException("Request not found") }

    private fun view(request: GameRequest): RequestV1 {
        val actor = access.principal()
        return RequestV1(
            request.id!!.toString(),
            request.title,
            request.platform.name,
            request.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
            request.status.name,
            request.requester?.id?.toString(),
            request.voters.size,
            actor.userId?.let { id -> request.voters.any { it.id == id } },
            request.linkedGameId?.toString(),
            request.createdAt,
            request.updatedAt
        )
    }
}
