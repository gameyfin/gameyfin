package org.gameyfin.app.api

import org.gameyfin.app.GameyfinApplication
import org.gameyfin.app.api.security.ApiCredential
import org.gameyfin.app.api.security.ApiCredentialRepository
import org.gameyfin.app.api.security.ApiCredentialService
import org.gameyfin.app.api.security.CredentialKind
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldApiSource
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.requests.GameRequestRepository
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.core.io.ResourceLoader
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.*
import kotlin.test.*

@SpringBootTest(
    classes = [GameyfinApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.datasource.url=jdbc:h2:mem:public-api;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "management.server.port=0",
        "vaadin.productionMode=true",
        "vaadin.launch-browser=false"
    ]
)
@Import(PublicApiIntegrationTest.ProductionEntities::class)
class PublicApiIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    class ProductionEntities {
        @Bean
        fun persistenceManagedTypes(loader: ResourceLoader): PersistenceManagedTypes {
            val scanned = PersistenceManagedTypesScanner(loader).scan("org.gameyfin.app")
            return PersistenceManagedTypes.of(
                scanned.managedClassNames.filterNot { it.endsWith("CompanyScanRecord") },
                scanned.managedPackages
            )
        }
    }

    companion object {
        private val root = Files.createTempDirectory("gameyfin-api-test")

        @JvmStatic
        @DynamicPropertySource
        fun directories(properties: DynamicPropertyRegistry) {
            properties.add("gameyfin.plugins.install-directory") { root.resolve("plugins").toString() }
            properties.add("spring.content.fs.filesystem-root") { root.resolve("media").toString() }
            properties.add("gameyfin.backup.directory") { root.resolve("backups").toString() }
        }
    }

    @Autowired
    lateinit var environment: Environment

    @Autowired
    lateinit var credentials: ApiCredentialRepository

    @Qualifier("apiCredentialService")
    @Autowired
    lateinit var credentialService: ApiCredentialService

    @Autowired
    lateinit var users: UserRepository

    @Autowired
    lateinit var libraries: LibraryRepository

    @Autowired
    lateinit var games: GameRepository

    @Autowired
    lateinit var transactions: PlatformTransactionManager

    @Autowired
    lateinit var mapper: JsonMapper

    @Autowired
    lateinit var requests: GameRequestRepository

    @Autowired
    lateinit var configService: ConfigService

    private val client = HttpClient.newHttpClient()

    private fun request(
        path: String,
        key: String? = null,
        method: String = "GET",
        body: String? = null,
        contentType: String = "application/json"
    ): HttpResponse<String> {
        val builder =
            HttpRequest.newBuilder(URI.create("http://localhost:${environment.getProperty("local.server.port")}$path"))
        key?.let { builder.header("X-API-Key", it) }
        if (body != null) builder.header("Content-Type", contentType)
        return client.send(
            builder.method(
                method,
                body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
            ).build(), HttpResponse.BodyHandlers.ofString()
        )
    }

    private fun key(scopes: Set<String>, owner: User? = null): String {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { (it + 1).toByte() })
        val credential = credentials.save(
            ApiCredential(
                kind = if (owner == null) CredentialKind.SERVICE else CredentialKind.PERSONAL,
                name = "test",
                secretDigest = ApiCredentialService.digest(secret),
                owner = owner,
                scopes = scopes.toMutableSet()
            )
        )
        return ApiCredentialService.prefix(credential.kind) + credential.id + "." + secret
    }

    @BeforeEach
    fun setup() {
        //Enable Swagger UI for the integration tests, since it is disabled by default in production mode.
        configService.set(ConfigProperties.Security.SwaggerUiEnabled, true)
    }

    @Test
    fun `catalog requests use independent schemas and enforce key scope and field visibility`() {
        val library = libraries.save(Library(name = "Public API catalog"))
        val game = games.save(
            Game(
                library = library,
                title = "Integration game",
                metadata = GameMetadata("/private/hidden-game")
            )
        )
        val readKey = key(setOf("games:read", "libraries:read"))
        assertEquals(CredentialKind.SERVICE, credentialService.authenticate(readKey).actor.kind)
        assertEquals(401, request("/v1/games").statusCode())
        val response = request("/v1/games?q=Integration&size=1", readKey)
        assertEquals(200, response.statusCode(), response.body())
        val json = mapper.readTree(response.body())
        assertEquals(game.id.toString(), json["items"][0]["id"].asString())
        assertFalse(response.body().contains("/private/"))
        assertEquals(400, request("/v1/games?size=201", readKey).statusCode())
        assertEquals(400, request("/v1/games?externalProvider=steam", readKey).statusCode())
        assertEquals(200, request("/v1/games?externalProvider=steam&externalId=123", readKey).statusCode())
        assertEquals(403, request("/v1/libraries/${library.id}/configuration", readKey).statusCode())
        assertEquals(403, request("/v1/games/${game.id}", readKey, "DELETE").statusCode())
        assertEquals(404, request("/v1/games/9223372036854775807", readKey).statusCode())
    }

    @Test
    fun `service metadata edits support null clearing attribution and separate deletion rights`() {
        val library = libraries.save(Library(name = "Public API edits"))
        val game = games.save(
            Game(
                library = library,
                title = "Original",
                summary = "Remove me",
                metadata = GameMetadata("/private/edit-game")
            )
        )
        val writeKey = key(setOf("games:write"))
        val response = request(
            "/v1/games/${game.id}",
            writeKey,
            "PATCH",
            """{"title":"Edited","summary":null}""",
            "application/merge-patch+json"
        )
        assertEquals(200, response.statusCode(), response.body())
        assertEquals("Edited", mapper.readTree(response.body())["title"].asString())
        assertTrue(mapper.readTree(response.body())["summary"].isNull)
        TransactionTemplate(transactions).executeWithoutResult {
            val stored = games.findById(game.id!!).get()
            assertIs<GameFieldApiSource>(stored.metadata.fields.getValue("title").source)
            assertIs<GameFieldApiSource>(stored.metadata.fields.getValue("summary").source)
        }
        assertEquals(
            400,
            request(
                "/v1/games/${game.id}",
                writeKey,
                "PATCH",
                """{"id":"20"}""",
                "application/merge-patch+json"
            ).statusCode()
        )
        assertEquals(403, request("/v1/games/${game.id}", writeKey, "DELETE").statusCode())
    }

    @Test
    fun `personal keys observe role changes and disabled accounts`() {
        val owner = users.save(
            User(
                username = "api-test-user",
                email = "api-test@example.test",
                enabled = true,
                roles = listOf(Role.ADMIN)
            )
        )
        val token = key(emptySet(), owner)
        assertEquals(200, request("/v1/tasks", token).statusCode())
        owner.roles = listOf(Role.USER)
        users.save(owner)
        assertEquals(403, request("/v1/tasks", token).statusCode())
        owner.enabled = false
        users.save(owner)
        assertEquals(401, request("/v1/me", token).statusCode())
        assertEquals(403, request("/v1/me", key(setOf("instance:read"))).statusCode())
    }

    @Test
    fun `user endpoints expose sensitive profile fields only to administrators`() {
        val owner = users.save(
            User(
                username = "api-profile", email = "profile@example.test",
                password = "private-password-hash", oidcProviderId = "private-subject", emailConfirmed = true,
                enabled = true, roles = listOf(Role.ADMIN)
            )
        )
        val token = key(emptySet(), owner)
        val paths = listOf("/v1/users?size=200", "/v1/users/${owner.id}", "/v1/users/by-username/${owner.username}")
        for (role in listOf(Role.ADMIN, Role.SUPERADMIN, Role.USER)) {
            owner.roles = listOf(role)
            users.save(owner)
            for (path in paths) {
                val response = request(path, token)
                assertEquals(200, response.statusCode(), response.body())
                val json = mapper.readTree(response.body())
                val profile =
                    if (path.contains("?")) json["items"].first { it["id"].asString() == owner.id.toString() } else json
                assertEquals(owner.username, profile["username"].asString())
                assertEquals(owner.id.toString(), profile["id"].asString())
                val sensitive = setOf("email", "emailConfirmed", "enabled", "managedBySso", "roles")
                for (field in sensitive) assertEquals(role != Role.USER, profile.has(field), "$role: $field")
                if (role != Role.USER) {
                    assertEquals(owner.email, profile["email"].asString())
                    assertTrue(profile["emailConfirmed"].asBoolean())
                    assertTrue(profile["enabled"].asBoolean())
                    assertTrue(profile["managedBySso"].asBoolean())
                    assertEquals(role.name, profile["roles"][0].asString())
                }
                assertFalse(response.body().contains("private-password-hash"))
                assertFalse(response.body().contains("private-subject"))
            }
            val me = request("/v1/me", token)
            assertEquals(200, me.statusCode(), me.body())
            assertEquals(owner.id.toString(), mapper.readTree(me.body())["id"].asString())
        }
        val service = key(setOf("users:read"))
        for (path in paths) {
            assertEquals(401, request(path).statusCode())
            assertEquals(403, request(path, key(setOf("instance:read"))).statusCode())
            val response = request(path, service)
            assertEquals(200, response.statusCode(), response.body())
            val json = mapper.readTree(response.body())
            val profiles = if (path.contains("?")) json["items"].toList() else listOf(json)
            for (profile in profiles) {
                for (field in setOf(
                    "email",
                    "emailConfirmed",
                    "enabled",
                    "managedBySso",
                    "roles",
                    "password",
                    "oidcProviderId"
                )) {
                    assertFalse(profile.has(field), field)
                }
            }
        }
        assertEquals(404, request("/v1/users/9223372036854775807", service).statusCode())
        assertEquals(404, request("/v1/users/by-username/missing-user", service).statusCode())
        assertEquals(400, request("/v1/users/invalid", service).statusCode())
        assertEquals(400, request("/v1/users?size=201", service).statusCode())
        assertEquals(403, request("/v1/me", service).statusCode())
    }

    @Test
    fun `last used updates cannot undo concurrent revocation`() {
        val token = key(setOf("games:read"))
        val id = token.substring(7, 43)
        TransactionTemplate(transactions).executeWithoutResult {
            val stale = credentials.findById(id).get()
            TransactionTemplate(transactions).apply {
                propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            }.executeWithoutResult {
                credentials.findById(id).get().revokedAt = Instant.now()
            }
            stale.lastUsedAt = Instant.now()
        }
        assertNotNull(credentials.findById(id).get().revokedAt)
        assertEquals(401, request("/v1/games", token).statusCode())
    }

    @Test
    fun `regenerated credentials persist settings reject old secrets and can be deleted after revocation`() {
        val owner = users.save(
            User(
                username = "api-rotation-admin", email = "rotation@example.test",
                enabled = true, roles = listOf(Role.ADMIN)
            )
        )
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                owner.username, null,
                listOf(org.springframework.security.core.authority.SimpleGrantedAuthority(Role.Names.ADMIN))
            )
        try {
            for (serviceKey in listOf(false, true)) {
                val expiry = Instant.now().plusSeconds(86400)
                val original = credentialService.create(
                    serviceKey, org.gameyfin.app.api.security.CredentialInput(
                        "Rotation test", expiry, if (serviceKey) setOf("games:read") else emptySet()
                    )
                )
                assertEquals(200, request("/v1/games", original.secret).statusCode())
                val regenerated = credentialService.regenerate(original.credential.id, serviceKey)
                val stored = credentials.findById(original.credential.id).orElseThrow()
                assertEquals(original.credential.name, stored.name)
                assertEquals(expiry.toEpochMilli(), stored.expiresAt!!.toEpochMilli())
                assertEquals(original.credential.scopes, regenerated.credential.scopes)
                assertEquals(401, request("/v1/games", original.secret).statusCode())
                assertEquals(200, request("/v1/games", regenerated.secret).statusCode())
                assertFailsWith<IllegalArgumentException> { credentialService.delete(stored.id, serviceKey) }
                credentialService.revoke(stored.id, serviceKey)
                credentialService.delete(stored.id, serviceKey)
                assertFalse(credentials.existsById(stored.id))
                assertEquals(401, request("/v1/games", regenerated.secret).statusCode())
            }
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext()
        }
    }

    @Test
    fun `deleting a creator removes personal tokens and preserves service keys`() {
        val owner = users.save(
            User(
                username = "api-deleted",
                email = "api-deleted@example.test",
                enabled = true,
                roles = listOf(Role.ADMIN)
            )
        )
        val personal = key(emptySet(), owner)
        val independent = credentials.save(
            ApiCredential(
                kind = CredentialKind.SERVICE, name = "Independent",
                secretDigest = "0".repeat(64), creator = owner, scopes = mutableSetOf("games:read")
            )
        )
        // Existing user-deletion notifications resolve their base URL from the browser request.
        RequestContextHolder.setRequestAttributes(
            ServletRequestAttributes(MockHttpServletRequest())
        )
        try {
            users.deleteById(owner.id!!)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
        assertFalse(credentials.existsById(personal.substring(7, 43)))
        assertNull(credentials.findById(independent.id).get().creator)
    }

    @Test
    fun `library creation and multipart media replacement use the public contract`() {
        val token = key(setOf("libraries:write", "games:write"))
        val directory = Files.createDirectories(root.resolve("library"))
        val body = mapper.writeValueAsString(
            mapOf(
                "name" to "Created library",
                "directories" to listOf(mapOf("internalPath" to directory.toString()))
            )
        )
        val created = request("/v1/libraries", token, "POST", body)
        assertEquals(201, created.statusCode(), created.body())
        val id = mapper.readTree(created.body())["id"].asString()
        val game = games.save(
            Game(
                library = libraries.findById(id.toLong()).get(),
                title = "Artwork",
                metadata = GameMetadata(directory.resolve("game").toString())
            )
        )
        val multipart =
            "--api-boundary\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n{\"cover\":null,\"header\":null,\"images\":[],\"videoUrls\":[],\"mediaOrder\":[]}\r\n--api-boundary--\r\n"
        val replaced =
            request("/v1/games/${game.id}/media", token, "PUT", multipart, "multipart/form-data; boundary=api-boundary")
        assertEquals(200, replaced.statusCode(), replaced.body())
        assertTrue(mapper.readTree(replaced.body())["cover"].isNull)
    }

    @Test
    fun `collection creation rejects unknown fields and membership is idempotent`() {
        val token = key(setOf("collections:write", "collections:read", "games:read"))
        assertEquals(
            400,
            request("/v1/collections", token, "POST", """{"name":"Invalid","unknown":true}""").statusCode()
        )
        val created = request("/v1/collections", token, "POST", """{"name":"API collection"}""")
        assertEquals(201, created.statusCode(), created.body())
        val id = mapper.readTree(created.body())["id"].asString()
        val library = libraries.save(Library(name = "Membership"))
        val game = games.save(Game(library = library, title = "Member", metadata = GameMetadata("/private/member")))
        repeat(2) { assertEquals(204, request("/v1/collections/$id/games/${game.id}", token, "PUT").statusCode()) }
        assertEquals(1, mapper.readTree(request("/v1/collections/$id/games", token).body())["totalElements"].asInt())
        repeat(2) { assertEquals(204, request("/v1/collections/$id/games/${game.id}", token, "DELETE").statusCode()) }
        assertEquals(0, mapper.readTree(request("/v1/collections/$id/games", token).body())["totalElements"].asInt())
    }

    @Test
    fun `personal votes preserve identity and service keys cannot impersonate requesters`() {
        val owner = users.save(
            User(
                username = "api-voter",
                email = "api-voter@example.test",
                enabled = true,
                roles = listOf(Role.USER)
            )
        )
        val token = key(emptySet(), owner)
        val entry = requests.save(
            GameRequest(
                title = "Vote test", release = Instant.parse("2020-01-01T00:00:00Z"),
                status = GameRequestStatus.PENDING,
                platform = Platform.entries.first(), requester = owner
            )
        )
        val service = key(setOf("requests:read", "requests:write", "requests:delete"))
        assertEquals(403, request("/v1/requests/${entry.id}/vote", service, "PUT").statusCode())
        repeat(2) { assertEquals(204, request("/v1/requests/${entry.id}/vote", token, "PUT").statusCode()) }
        assertEquals(1, mapper.readTree(request("/v1/requests/${entry.id}", token).body())["voteCount"].asInt())
        repeat(2) { assertEquals(204, request("/v1/requests/${entry.id}/vote", token, "DELETE").statusCode()) }
        assertEquals(0, mapper.readTree(request("/v1/requests/${entry.id}", token).body())["voteCount"].asInt())
        val deleted = request("/v1/requests/${entry.id}", service, "DELETE")
        assertEquals(204, deleted.statusCode(), deleted.body())
    }

    @Test
    fun `public documentation describes only versioned endpoints and stays compatible with baseline`() {
        assertEquals(200, request("/v3/api-docs.yaml/v1").statusCode())
        val response = request("/v3/api-docs/v1")
        assertEquals(200, response.statusCode(), response.body())
        val spec = mapper.readTree(response.body())
        assertEquals("X-API-Key", spec["components"]["securitySchemes"]["apiKey"]["name"].asString())
        val paths = spec["paths"]
        assertTrue(paths.propertyNames().all { it.startsWith("/v1/") })
        assertTrue(paths.has("/v1/games"))
        assertFalse(response.body().contains("CredentialEndpoint"))
        val generated = Path.of("build", "openapi", "v1.json")
        Files.createDirectories(generated.parent)
        Files.writeString(generated, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(spec))
        val baseline = Path.of("src", "test", "resources", "openapi", "v1.json")
        assertTrue(Files.exists(baseline), "Review build/openapi/v1.json and save the initial contract baseline")
        val previous = mapper.readTree(Files.readString(baseline))
        // Exact contract comparison deliberately also requires review of additive changes.
        // Server ports are runtime-specific and do not belong to the compatibility contract.
        assertEquals(
            previous["paths"],
            spec["paths"],
            "Public operations changed: review compatibility before updating the baseline"
        )
        assertEquals(
            previous["components"],
            spec["components"],
            "Public schemas changed: review compatibility before updating the baseline"
        )
    }
}
