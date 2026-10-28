package org.gameyfin.app.api.v1

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
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
    fun publicApiV1(): GroupedOpenApi = GroupedOpenApi.builder().group("v1")
        .packagesToScan("org.gameyfin.app.api.v1").pathsToMatch("/v1/**")
        .addOpenApiCustomizer { api ->
            val inputs = setOf(
                "GameMediaV1", "ImageSelectionV1", "CreateRequestV1", "CreateLibraryV1", "DirectoryV1",
                "ScanV1", "CreateCollectionV1", "RequestStatusV1", "LibraryPatchV1", "GamePatchV1", "CollectionPatchV1"
            )
            api.components.schemas.forEach { (name, schema) ->
                if (name !in inputs) schema.required = schema.properties?.keys?.sorted()
            }
            mapOf(
                "CreateRequestV1" to listOf("title", "platform"), "CreateLibraryV1" to listOf("name", "directories"),
                "DirectoryV1" to listOf("internalPath"), "CreateCollectionV1" to listOf("name"),
                "RequestStatusV1" to listOf("status")
            ).forEach { (name, fields) -> api.components.schemas[name]?.required = fields }
            api.components.schemas["UserV1"]?.required = listOf("hasAvatar", "id", "username")
            api.components.addSchemas(
                "ApiProblemV1", ObjectSchema()
                    .addProperty("type", StringSchema()).addProperty("title", StringSchema())
                    .addProperty("status", IntegerSchema()).addProperty("detail", StringSchema())
                    .addProperty("instance", StringSchema()).addProperty("code", StringSchema())
            )
            api.paths.values.flatMap { it.readOperations() }.forEach { operation ->
                val success = when (operation.operationId) {
                    "createLibrary", "createCollection", "createRequest" -> "201"
                    "deleteLibrary", "deleteCollection", "scanLibrary" -> "202"
                    "deleteGame", "deleteRequest", "addCollectionGame", "removeCollectionGame", "voteForRequest", "removeRequestVote" -> "204"
                    else -> "200"
                }
                if (success != "200") operation.responses.remove("200")?.let {
                    it.description = when (success) {
                        "201" -> "Created"; "202" -> "Accepted"; else -> "No content"
                    }
                    if (success == "204") it.content = null
                    operation.responses.addApiResponse(success, it)
                }
                operation.responses.values.forEach { response ->
                    response.content?.remove("*/*")?.let { media ->
                        response.content.addMediaType(
                            if (operation.operationId == "getGameImage") "image/*" else "application/json",
                            media
                        )
                    }
                }
                mapOf(
                    "400" to "Invalid request", "401" to "Missing, invalid, expired or revoked key",
                    "403" to "Insufficient permissions", "404" to "Resource not found", "409" to "Conflicting change",
                    "500" to "Internal error"
                ).forEach { (code, description) ->
                    operation.responses.addApiResponse(
                        code, ApiResponse().description(description).content(
                            Content()
                                .addMediaType(
                                    "application/problem+json",
                                    MediaType().schema(ObjectSchema().`$ref`("#/components/schemas/ApiProblemV1"))
                                )
                        )
                    )
                }
                if (operation.operationId == "getGameImage") operation.responses.addApiResponse(
                    "304",
                    ApiResponse().description("Not modified")
                )
                if (operation.operationId == "getHealth") operation.responses.addApiResponse(
                    "503", ApiResponse()
                        .description("Instance is unhealthy").content(
                            Content().addMediaType(
                                "application/json",
                                MediaType().schema(ObjectSchema().`$ref`("#/components/schemas/HealthV1"))
                            )
                        )
                )
                if (operation.operationId == "replaceGameMedia") {
                    operation.requestBody.content["multipart/form-data"]?.apply {
                        addEncoding("metadata", Encoding().contentType("application/json"))
                        schema.additionalProperties = StringSchema().format("binary")
                    }
                }
            }
        }.build()

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
}
