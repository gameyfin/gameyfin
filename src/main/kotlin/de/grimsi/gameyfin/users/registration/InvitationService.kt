package de.grimsi.gameyfin.users.registration

import de.grimsi.gameyfin.core.Utils
import de.grimsi.gameyfin.core.events.UserInvitationEvent
import de.grimsi.gameyfin.core.events.UserRegistrationEvent
import de.grimsi.gameyfin.shared.token.TokenDto
import de.grimsi.gameyfin.shared.token.TokenRepository
import de.grimsi.gameyfin.shared.token.TokenService
import de.grimsi.gameyfin.shared.token.TokenType.Invitation
import de.grimsi.gameyfin.shared.token.TokenValidationResult
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class InvitationService(
    tokenRepository: TokenRepository,
    private val userService: UserService,
    private val eventPublisher: ApplicationEventPublisher
) : TokenService<Invitation>(Invitation, tokenRepository) {

    companion object {
        private const val EMAIL_KEY = "email"
    }

    fun createInvitation(email: String): TokenDto {
        if (userService.existsByEmail(email))
            throw IllegalStateException("User with email ${Utils.maskEmail(email)} is already registered")

        val auth: Authentication = SecurityContextHolder.getContext().authentication
        val user = userService.getByUsername(auth.name) ?: throw IllegalStateException("User not found")
        val payload = mapOf(EMAIL_KEY to email)
        val token = super.generateWithPayload(user, payload)

        eventPublisher.publishEvent(UserInvitationEvent(this, token, Utils.getBaseUrl(), email))
        return TokenDto(token)
    }

    fun getAssociatedEmail(secret: String): String? {
        val payload = super.getPayload(secret) ?: return null
        return payload[EMAIL_KEY]
    }

    fun acceptInvitation(secret: String, registration: UserRegistrationDto): TokenValidationResult {
        val invitationToken = super.get(secret, Invitation) ?: return TokenValidationResult.INVALID
        val email = invitationToken.payload[EMAIL_KEY] ?: return TokenValidationResult.INVALID
        if (invitationToken.expired) return TokenValidationResult.EXPIRED

        val user = userService.registerUserFromInvitation(registration, email)
        super.delete(invitationToken)
        eventPublisher.publishEvent(UserRegistrationEvent(this, user, Utils.getBaseUrl()))

        return TokenValidationResult.VALID
    }
}