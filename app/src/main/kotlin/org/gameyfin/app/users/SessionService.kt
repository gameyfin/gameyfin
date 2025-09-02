package org.gameyfin.app.users

import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.entities.User
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.session.SessionInformation
import org.springframework.security.core.session.SessionRegistry
import org.springframework.stereotype.Service

@Service
class SessionService(private val sessionRegistry: SessionRegistry) {

    fun logoutAllSessions() {
        val auth = getCurrentAuth()
        val sessions: List<SessionInformation> = sessionRegistry.getAllSessions(auth?.principal, false)
        for (sessionInfo in sessions) {
            sessionInfo.expireNow()
        }
        SecurityContextHolder.clearContext()
    }

    fun logoutAllSessions(user: User) {
        val sessions: List<SessionInformation> = sessionRegistry.getAllSessions(user, false)
        for (sessionInfo in sessions) {
            sessionInfo.expireNow()
        }
    }
}