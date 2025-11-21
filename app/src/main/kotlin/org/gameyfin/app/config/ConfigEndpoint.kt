package org.gameyfin.app.config

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.config.dto.ConfigEntryDto
import org.gameyfin.app.config.dto.ConfigUpdateDto
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.springframework.scheduling.support.CronExpression
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class ConfigEndpoint(
    private val configService: ConfigService,
) {
    companion object {
        val log = KotlinLogging.logger { }
    }

    /** CRUD endpoints for admins **/

    @PermitAll
    fun subscribe(): Flux<List<ConfigUpdateDto>> {
        return if (isCurrentUserAdmin()) ConfigService.subscribe()
        else Flux.empty()
    }

    fun getAll(): List<ConfigEntryDto> = configService.getAll()

    fun update(update: ConfigUpdateDto) = configService.update(update)

    /**
     * Validates a cron expression because Spring has a custom syntax for cron expressions.
     * @see: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/support/CronExpression.html#parse(java.lang.String)
     */
    fun validateCronExpression(cronExpression: String): Boolean {
        return CronExpression.isValidExpression(cronExpression)
    }

    /** Specific read-only endpoints for all users **/
    @DynamicPublicAccess
    @AnonymousAllowed
    fun areGameRequestsEnabled(): Boolean = configService.get(ConfigProperties.Requests.Games.Enabled) == true

    @DynamicPublicAccess
    @AnonymousAllowed
    fun areGuestsAllowedToRequestGames(): Boolean =
        configService.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) == true

    @DynamicPublicAccess
    @AnonymousAllowed
    fun showRecentlyAddedOnHomepage(): Boolean =
        configService.get(ConfigProperties.UI.Homepage.ShowRecentlyAddedGames) == true
}
