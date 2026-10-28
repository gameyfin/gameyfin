package org.gameyfin.app.api.v1

import io.swagger.v3.oas.annotations.Operation
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.dto.MeV1
import org.gameyfin.app.api.v1.dto.PageV1
import org.gameyfin.app.api.v1.dto.UserV1
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1")
class UserControllerV1(private val access: ApiAccess, private val users: UserRepository) {

    @GetMapping("/me")
    @Operation(
        operationId = "getCurrentUser",
        description = "Personal tokens only. Request write/delete permissions remain subject to ownership and administrator restrictions."
    )
    fun me(): MeV1 {
        val actor = access.personal()
        return MeV1(actor.userId!!.toString(), actor.name, if (isCurrentUserAdmin()) ApiScope.all else ApiScope.user)
    }

    @GetMapping("/users")
    @Transactional(readOnly = true)
    @Operation(
        operationId = "getAllUsers",
        description = "Requires users:read. Only administrator personal tokens receive full user information. sort: id."
    )
    fun getAllUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int, @RequestParam(defaultValue = "id") sort: String
    ): PageV1<UserV1> {
        access.require(ApiScope.USERS_READ)
        return users.findAll(apiPage(page, size, sort, setOf("id"))).publicPage(::view)
    }

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    @Operation(
        operationId = "getUserById",
        description = "Requires users:read. Only administrator personal tokens receive full user information."
    )
    fun getUserById(@PathVariable id: String): UserV1 {
        access.require(ApiScope.USERS_READ)
        return view(users.findById(apiId(id)).orElseThrow { NoSuchElementException("User not found") })
    }

    @GetMapping("/users/by-username/{username}")
    @Transactional(readOnly = true)
    @Operation(
        operationId = "getUserByUsername",
        description = "Requires users:read. Only administrator personal tokens receive full user information."
    )
    fun getUserByUsername(@PathVariable username: String): UserV1 {
        access.require(ApiScope.USERS_READ)
        return view(users.findByUsername(username) ?: throw NoSuchElementException("User not found"))
    }

    private fun view(user: User): UserV1 {
        val profile = UserV1(user.id!!.toString(), user.username, user.avatar != null, user.avatar?.id?.toString())
        return if (isCurrentUserAdmin()) profile.copy(
            email = user.email, emailConfirmed = user.emailConfirmed, enabled = user.enabled,
            managedBySso = user.oidcProviderId != null, roles = user.roles.map { it.name }
        ) else profile
    }
}
