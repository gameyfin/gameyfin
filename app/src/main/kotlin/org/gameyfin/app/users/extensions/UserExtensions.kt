package org.gameyfin.app.users.extensions

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserInfoDto
import org.gameyfin.app.users.entities.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

fun User.toUserInfoDto(): UserInfoDto {
    return UserInfoDto(
        id = this.id!!,
        username = this.username,
        hasAvatar = this.avatar != null,
        avatarId = this.avatar?.id
    )
}

fun User.toExtendedUserInfoDto(): ExtendedUserInfoDto {
    return ExtendedUserInfoDto(
        id = this.id!!,
        username = this.username,
        email = this.email,
        emailConfirmed = this.emailConfirmed,
        enabled = this.enabled,
        hasAvatar = this.avatar != null,
        avatarId = this.avatar?.id,
        managedBySso = this.oidcProviderId != null,
        roles = this.roles
    )
}

fun Collection<Role>.toAuthorities(): List<GrantedAuthority> {
    return this.map { r -> SimpleGrantedAuthority(r.roleName) }
}