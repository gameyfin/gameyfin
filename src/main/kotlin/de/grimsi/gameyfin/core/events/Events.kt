package de.grimsi.gameyfin.core.events

import de.grimsi.gameyfin.shared.token.Token
import de.grimsi.gameyfin.shared.token.TokenType.*
import de.grimsi.gameyfin.users.entities.User
import org.springframework.context.ApplicationEvent

class UserInvitationEvent(source: Any, val token: Token<Invitation>, val baseUrl: String, val email: String) :
    ApplicationEvent(source)

class UserRegistrationWaitingForApprovalEvent(source: Any, val newUser: User) : ApplicationEvent(source)

class AccountStatusChangedEvent(source: Any, val user: User, val baseUrl: String) : ApplicationEvent(source)

class EmailNeedsConfirmationEvent(source: Any, val token: Token<EmailConfirmation>, val baseUrl: String) :
    ApplicationEvent(source)

class RegistrationAttemptWithExistingEmailEvent(source: Any, val existingUser: User, val baseUrl: String) :
    ApplicationEvent(source)

class PasswordResetRequestEvent(source: Any, val token: Token<PasswordReset>, val baseUrl: String) :
    ApplicationEvent(source)

class AccountDeletedEvent(source: Any, val user: User, val baseUrl: String) : ApplicationEvent(source)

class GameRequestEvent(source: Any) : ApplicationEvent(source)

class GameRequestApprovalEvent(source: Any) : ApplicationEvent(source)