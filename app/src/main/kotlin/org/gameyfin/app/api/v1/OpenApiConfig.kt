package org.gameyfin.app.api.v1

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.*
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun publicApiDescription(): OpenAPI = OpenAPI()
        .servers(listOf(Server().url("/")))
        .info(
            Info().title("Gameyfin public API").version("1.0.0")
                .description("Stable REST-API for integration with 3rd party applications and services. Requires a personal access tokens or API key to use.")
        )
        .components(
            Components().addSecuritySchemes(
                "apiKey", SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.HEADER).name("X-API-Key")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("apiKey"))

    @Bean
    fun publicApiV1(): GroupedOpenApi = GroupedOpenApi.builder().group("v1")
        .packagesToScan("org.gameyfin.app.api.v1").pathsToMatch("/v1/**")
        .addOpenApiCustomizer(OpenApiRequiredFieldsCustomizer)
        .addOpenApiCustomizer { api ->
            api.components.addSchemas("ApiProblemV1", apiProblemSchema())
            api.paths.values.flatMap { it.readOperations() }.forEach { customizeOperation(it) }
        }.build()

    private val errorResponses = mapOf(
        "400" to "Invalid request",
        "401" to "Missing, invalid, expired or revoked key",
        "403" to "Insufficient permissions",
        "404" to "Resource not found",
        "409" to "Conflicting change",
        "500" to "Internal error"
    )

    private fun apiProblemSchema() = ObjectSchema()
        .addProperty("type", StringSchema()).addProperty("title", StringSchema())
        .addProperty("status", IntegerSchema()).addProperty("detail", StringSchema())
        .addProperty("instance", StringSchema()).addProperty("code", StringSchema())

    private fun customizeOperation(operation: Operation) {
        operation.responses.values.forEach(::useJsonMediaType)
        errorResponses.forEach { (code, description) ->
            operation.responses.addApiResponse(code, errorResponse(description))
        }
        addBinaryUploadParts(operation)
    }

    private fun useJsonMediaType(response: ApiResponse) {
        response.content?.remove("*/*")?.let { response.content.addMediaType("application/json", it) }
    }

    private fun errorResponse(description: String) = ApiResponse().description(description).content(
        Content().addMediaType(
            "application/problem+json",
            MediaType().schema(ObjectSchema().`$ref`("#/components/schemas/ApiProblemV1"))
        )
    )

    private fun addBinaryUploadParts(operation: Operation) {
        if (operation.operationId == "replaceGameMedia") {
            operation.requestBody.content["multipart/form-data"]?.schema?.additionalProperties =
                StringSchema().format("binary")
        }
    }
}
