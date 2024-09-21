package de.grimsi.gameyfin.notifications.events

import org.springframework.context.ApplicationEvent

class UserInvitationEvent(source: Any) : ApplicationEvent(source)

class UserRegistrationEvent(source: Any) : ApplicationEvent(source)

class PasswordResetRequestEvent(source: Any) : ApplicationEvent(source)

class GameRequestEvent(source: Any) : ApplicationEvent(source)

class GameRequestApprovalEvent(source: Any) : ApplicationEvent(source)