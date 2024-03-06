package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.users.dto.UserInfo
import dev.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Endpoint
class UserEndpoint {

    @PermitAll
    fun getUserInfo(): UserInfo {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        val authorities: List<String> = auth.authorities.map { g: GrantedAuthority -> g.authority }
        return UserInfo(auth.name, authorities)
    }
}