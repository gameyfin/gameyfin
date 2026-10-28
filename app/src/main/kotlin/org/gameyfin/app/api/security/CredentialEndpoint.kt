package org.gameyfin.app.api.security

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@Endpoint
@PermitAll
class CredentialEndpoint(private val credentials: ApiCredentialService) {
    fun list(service: Boolean): List<CredentialView> = credentials.list(service)
    fun scopes(): Set<String> = ApiScopes.all
    fun create(service: Boolean, input: CredentialInput): CreatedCredential {
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.response?.setHeader("Cache-Control", "no-store")
        return credentials.create(service, input)
    }
    fun update(id: String, service: Boolean, input: CredentialInput): CredentialView = credentials.update(id, service, input)
    fun revoke(id: String, service: Boolean) = credentials.revoke(id, service)
    fun delete(id: String, service: Boolean) = credentials.delete(id, service)
    fun regenerate(id: String, service: Boolean): CreatedCredential {
        (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.response?.setHeader("Cache-Control", "no-store")
        return credentials.regenerate(id, service)
    }
}
