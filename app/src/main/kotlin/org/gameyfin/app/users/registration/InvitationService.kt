package org.gameyfin.app.users.registration

import org.gameyfin.app.core.events.AccountStatusChangedEvent
import org.gameyfin.app.core.events.UserInvitationEvent
import org.gameyfin.app.shared.token.TokenDto
import org.gameyfin.app.shared.token.TokenRepository
import org.gameyfin.app.users.UserService
import org.gameyfin.app.core.Utils
import org.gameyfin.app.shared.token.TokenService
import org.gameyfin.app.shared.token.TokenType
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.enums.UserInvitationAcceptanceResult
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class InvitationService(
    tokenRepository: TokenRepository,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<TokenType.Invitation>(TokenType.Invitation, tokenRepository) {

    companion object {
        private const val EMAIL_KEY = "email"
    }

    fun createInvitation(email: String): TokenDto {
        if (userService.existsByEmail(email))
            throw IllegalStateException("User with email ${Utils.Companion.maskEmail(email)} is already registered")

        val auth: Authentication = SecurityContextHolder.getContext().authentication
        val user = userService.getByUsername(auth.name) ?: throw IllegalStateException("User not found")
        val payload = mapOf(EMAIL_KEY to email)
        val token = super.generateWithPayload(user, payload)

        eventPublisher.publishEvent(UserInvitationEvent(this, token, Utils.Companion.getBaseUrl(), email))
        return TokenDto(token)
    }

    fun getAssociatedEmail(secret: String): String? {
        val payload = super.getPayload(secret) ?: return null
        return payload[EMAIL_KEY]
    }

    fun acceptInvitation(secret: String, registration: UserRegistrationDto): UserInvitationAcceptanceResult {
        val invitationToken = super.get(secret, TokenType.Invitation) ?: return UserInvitationAcceptanceResult.TOKEN_INVALID
        val email = invitationToken.payload[EMAIL_KEY] ?: return UserInvitationAcceptanceResult.TOKEN_INVALID
        if (invitationToken.expired) return UserInvitationAcceptanceResult.TOKEN_EXPIRED

        try {
            val user = userService.registerUserFromInvitation(registration, email)
            super.delete(invitationToken)
            eventPublisher.publishEvent(AccountStatusChangedEvent(this, user, Utils.Companion.getBaseUrl()))
        } catch (e: IllegalStateException) {
            return UserInvitationAcceptanceResult.USERNAME_TAKEN
        }

        return UserInvitationAcceptanceResult.SUCCESS
    }
}