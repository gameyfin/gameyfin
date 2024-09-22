package de.grimsi.gameyfin.core.events

import de.grimsi.gameyfin.users.entities.PasswordResetToken
import org.springframework.context.ApplicationEvent

class UserInvitationEvent(source: Any) : ApplicationEvent(source)

class UserRegistrationEvent(source: Any) : ApplicationEvent(source)

class PasswordResetRequestEvent(source: Any, val token: PasswordResetToken) : ApplicationEvent(source)

class GameRequestEvent(source: Any) : ApplicationEvent(source)

class GameRequestApprovalEvent(source: Any) : ApplicationEvent(source)