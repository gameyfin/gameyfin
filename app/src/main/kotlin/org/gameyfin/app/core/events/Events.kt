package org.gameyfin.app.core.events

import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.shared.token.Token
import org.gameyfin.app.shared.token.TokenType
import org.gameyfin.app.users.entities.User
import org.springframework.context.ApplicationEvent

class UserInvitationEvent(source: Any, val token: Token<TokenType.Invitation>, val baseUrl: String, val email: String) :
    ApplicationEvent(source)

class UserRegistrationWaitingForApprovalEvent(source: Any, val newUser: User) : ApplicationEvent(source)

class AccountStatusChangedEvent(source: Any, val user: User, val baseUrl: String) : ApplicationEvent(source)

class EmailNeedsConfirmationEvent(source: Any, val token: Token<TokenType.EmailConfirmation>, val baseUrl: String) :
    ApplicationEvent(source)

class RegistrationAttemptWithExistingEmailEvent(source: Any, val existingUser: User, val baseUrl: String) :
    ApplicationEvent(source)

class PasswordResetRequestEvent(source: Any, val token: Token<TokenType.PasswordReset>, val baseUrl: String) :
    ApplicationEvent(source)

class LibraryScanScheduleUpdatedEvent(source: Any) : ApplicationEvent(source)

class UserDeletedEvent(source: Any, val user: User, val baseUrl: String) : ApplicationEvent(source)
class UserUpdatedEvent(source: Any, val previousState: User, val currentState: User) : ApplicationEvent(source)

class GameCreatedEvent(source: Any, val game: Game) : ApplicationEvent(source)
class GameUpdatedEvent(source: Any, val previousState: Game, val currentState: Game) : ApplicationEvent(source)
class GameDeletedEvent(source: Any, val game: Game) : ApplicationEvent(source)

class LibraryCreatedEvent(source: Any, val library: Library) : ApplicationEvent(source)
class LibraryUpdatedEvent(source: Any, val currentState: Library) : ApplicationEvent(source)
class LibraryDeletedEvent(source: Any, val library: Library) : ApplicationEvent(source)