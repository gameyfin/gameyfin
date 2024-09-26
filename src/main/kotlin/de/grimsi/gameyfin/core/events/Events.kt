package de.grimsi.gameyfin.core.events

import de.grimsi.gameyfin.shared.token.Token
import de.grimsi.gameyfin.shared.token.TokenType.PasswordReset
import org.springframework.context.ApplicationEvent

class UserInvitationEvent(source: Any) : ApplicationEvent(source)

class UserRegistrationEvent(source: Any) : ApplicationEvent(source)

class PasswordResetRequestEvent(source: Any, val token: Token<PasswordReset>, val baseUrl: String) :
    ApplicationEvent(source)

class GameRequestEvent(source: Any) : ApplicationEvent(source)

class GameRequestApprovalEvent(source: Any) : ApplicationEvent(source)