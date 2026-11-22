package org.gameyfin.app.api

import org.gameyfin.app.GameyfinApplication
import org.gameyfin.app.api.security.ApiCredential
import org.gameyfin.app.api.security.ApiCredentialRepository
import org.gameyfin.app.api.security.ApiCredentialService
import org.gameyfin.app.api.security.CredentialKind
import org.gameyfin.app.collections.CollectionEndpoint
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionCreateDto
import org.gameyfin.app.collections.dto.CollectionUpdateDto
import org.gameyfin.app.collections.entities.CollectionVisibility
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
import org.gameyfin.app.social.GameSocialService
import org.gameyfin.app.social.PlayStatus
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
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
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
    @Test
    fun `ratings and play status stay independent and review moderation targets the inspected version`() {
        val library = libraries.save(Library(name = "Social activity"))
        val game = games.save(Game(library = library, title = "Activity game", metadata = GameMetadata("/activity/game")))
        val id = game.id!!
        val owner = socialMember("review-author")
        val reader = socialMember("review-reader")
        try {
            asMember(owner) {
                assertEquals(0L, social.get(id).ratingCount)
                assertNull(social.get(id).averageRating)
                assertFailsWith<IllegalArgumentException> { social.rate(id, 6) }
                social.rate(id, 5)
                assertTrue(social.setPlayStatus(id, PlayStatus.PLAYED))
                assertFalse(social.setPlayStatus(id, PlayStatus.PLAYED))
                social.saveReview(id, "First published")
                assertEquals("First published", social.get(id).reviews.single().text)
                social.rate(id, 2)
                assertEquals(2, social.get(id).myRating)
                social.rate(id, null)
                assertNull(social.get(id).myRating)
                assertEquals(PlayStatus.PLAYED, social.get(id).myPlayStatus)
                social.rate(id, 4)
            }
            asMember(reader) {
                social.rate(id, 3)
                val view = social.get(id)
                assertEquals(3.5, view.averageRating)
                assertEquals(2L, view.ratingCount)
                assertNull(view.myPlayStatus)
                assertNull(view.myReview)
            }

            configService.set(ConfigProperties.Social.RequireReviewApproval, true)
            asMember(owner) {
                social.saveReview(id, "First pending")
                assertEquals("First pending", social.get(id).myReview?.submittedText)
            }
            val first = social.moderationQueue().single { it.gameId == id && it.pending }
            asMember(owner) { social.saveReview(id, "Second pending") }
            assertFalse(social.moderate(id, owner.id!!, first.version, approve = true))
            assertEquals("First published", social.get(id).reviews.single().text)

            configService.set(ConfigProperties.Social.RequireReviewApproval, false)
            val second = social.moderationQueue().single { it.gameId == id && it.pending }
            assertEquals("Second pending", second.text)
            assertTrue(social.moderate(id, owner.id!!, second.version, approve = false))
            asMember(owner) { assertTrue(social.get(id).myReview!!.rejected) }

            configService.set(ConfigProperties.Social.RequireReviewApproval, true)
            asMember(owner) { social.saveReview(id, "Final pending") }
            val final = social.moderationQueue().single { it.gameId == id && it.pending }
            assertTrue(social.moderate(id, owner.id!!, final.version, approve = true))
            assertEquals("Final pending", social.get(id).reviews.single().text)
            assertTrue(social.removePublished(id, owner.id!!, final.version))

            configService.set(ConfigProperties.Social.EnableReviews, false)
            asMember(owner) {
                assertFalse(social.get(id).reviewsEnabled)
                assertTrue(social.get(id).reviews.isEmpty())
                assertFailsWith<AccessDeniedException> { social.saveReview(id, "Disabled") }
                assertFailsWith<AccessDeniedException> { social.deleteReview(id) }
                social.setPlayStatus(id, null)
                assertFalse(social.setPlayStatus(id, PlayStatus.PLAYED))
            }
            configService.set(ConfigProperties.Social.EnableReviews, true)
            asMember(owner) {
                social.saveReview(id, "Draft to delete")
                social.deleteReview(id)
                assertNull(social.get(id).myReview)
            }
        } finally {
            configService.set(ConfigProperties.Social.EnableReviews, true)
            configService.set(ConfigProperties.Social.RequireReviewApproval, false)
            games.deleteById(id)
        }
    }

    @Test
    fun `collection visibility applies to open streams direct links sync and the catalogue API`() {
        val library = libraries.save(Library(name = "Social collections"))
        val game = games.save(Game(library = library, title = "Collection game", metadata = GameMetadata("/collections/game")))
        val owner = socialMember("collection-owner")
        val reader = socialMember("collection-reader")
        val admin = socialMember("collection-admin", Role.ADMIN)
        val serviceToken = key(setOf("games:read", "collections:read"))
        val readerToken = key(emptySet(), reader)
        configService.set(ConfigProperties.Security.AllowPublicAccess, true)
        try {
            val id = asMember(owner) {
                collections.createCustom(CollectionCreateDto("Shared collection", gameIds = listOf(game.id!!)))
            }
            assertNotNull(asMember(owner) { collections.getDtoById(id) })
            assertNull(asMember(reader) { collections.getDtoById(id) })
            assertNull(asMember(admin) { collections.getDtoById(id) })
            assertNull(collections.getDtoById(id))
            assertFalse(id in asMember(reader) { collections.sync(Instant.EPOCH).allIds })
            assertEquals(404, request("/v1/collections/$id", readerToken).statusCode())
            assertEquals(404, request("/v1/collections/$id", serviceToken).statusCode())
            val filtered = request("/v1/games?collectionId=$id", serviceToken)
            assertEquals(200, filtered.statusCode(), filtered.body())
            assertEquals(0, mapper.readTree(filtered.body())["items"].size())

            val readerSnapshots = LinkedBlockingQueue<List<Long>>()
            val guestSnapshots = LinkedBlockingQueue<List<Long>>()
            val readerSubscription = asMember(reader) { collectionEndpoint.subscribeToCollections() }
                .subscribe { readerSnapshots.add(it.map { dto -> dto.id }) }
            val guestSubscription = collectionEndpoint.subscribeToCollections()
                .subscribe { guestSnapshots.add(it.map { dto -> dto.id }) }
            try {
                assertFalse(id in assertNotNull(readerSnapshots.poll(5, TimeUnit.SECONDS)))
                assertFalse(id in assertNotNull(guestSnapshots.poll(5, TimeUnit.SECONDS)))
                asMember(owner) { collections.updateOwned(CollectionUpdateDto(id, visibility = CollectionVisibility.MEMBERS_ONLY)) }
                awaitSnapshot(readerSnapshots, id, true)
                assertNull(collections.getDtoById(id))
                assertEquals(200, request("/v1/collections/$id", readerToken).statusCode())
                assertEquals(404, request("/v1/collections/$id", serviceToken).statusCode())

                asMember(owner) { collections.updateOwned(CollectionUpdateDto(id, visibility = CollectionVisibility.PUBLIC)) }
                awaitSnapshot(guestSnapshots, id, true)
                assertEquals(200, request("/v1/collections/$id", serviceToken).statusCode())
                val sorted = request("/v1/collections?sort=name", serviceToken)
                assertEquals(200, sorted.statusCode(), sorted.body())
                assertTrue(mapper.readTree(sorted.body())["items"].any { it["name"].asString() == "Shared collection" })
                asMember(owner) { collections.updateOwned(CollectionUpdateDto(id, visibility = CollectionVisibility.PRIVATE)) }
                awaitSnapshot(readerSnapshots, id, false)
                awaitSnapshot(guestSnapshots, id, false)
                assertFalse(id in asMember(reader) { collections.sync(Instant.EPOCH).allIds })
                assertFailsWith<AccessDeniedException> { asMember(reader) { collections.deleteOwned(id) } }
            } finally {
                readerSubscription.dispose()
                guestSubscription.dispose()
            }

            configService.set(ConfigProperties.Social.AllowUserCollections, false)
            asMember(owner) {
                assertFailsWith<AccessDeniedException> { collections.createCustom(CollectionCreateDto("Disabled")) }
                assertEquals("Renamed", collections.updateOwned(CollectionUpdateDto(id, name = "Renamed")).name)
            }
            val start = CountDownLatch(1)
            val favourite = Executors.newFixedThreadPool(2).use { executor ->
                val pending = (1..2).map {
                    executor.submit<Long> {
                        start.await()
                        asMember(owner) { collections.ensureFavourites() }
                    }
                }
                start.countDown()
                pending.map { it.get(10, TimeUnit.SECONDS) }.toSet().single()
            }
            asMember(owner) {
                collections.setFavourite(game.id!!, true)
                assertEquals(listOf(game.id!!), collections.getDtoById(favourite)?.gameIds)
                collections.updateOwned(CollectionUpdateDto(favourite, visibility = CollectionVisibility.PUBLIC))
                assertEquals(favourite, collections.ensureFavourites())
                collections.deleteOwned(id)
            }
            assertEquals(listOf(game.id!!), collections.getDtoById(favourite)?.gameIds)
        } finally {
            configService.set(ConfigProperties.Security.AllowPublicAccess, false)
            configService.set(ConfigProperties.Social.AllowUserCollections, true)
            games.deleteById(game.id!!)
        }
    }

    private fun awaitSnapshot(queue: LinkedBlockingQueue<List<Long>>, id: Long, expected: Boolean) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < end) {
            val snapshot = queue.poll(end - System.nanoTime(), TimeUnit.NANOSECONDS) ?: break
            if ((id in snapshot) == expected) return
        }
        fail("Collection $id visibility did not become $expected")
    }

    private fun socialMember(username: String, role: Role = Role.USER) =
        users.save(User(username = username, email = "$username@example.com", enabled = true, roles = listOf(role)))

    private fun <T> asMember(user: User, block: () -> T): T {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            user.username, null, user.roles.map { SimpleGrantedAuthority(it.roleName) }
        )
        return try {
            block()
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

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

    @Autowired
    lateinit var social: GameSocialService

    @Autowired
    lateinit var collections: CollectionService

    @Autowired
    lateinit var collectionEndpoint: CollectionEndpoint

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
    fun `game index exposes the complete ordered catalog and reflects deletions`() {
        val library = libraries.save(Library(name = "Index library"))
        val game = games.save(Game(library = library, title = "Index game", metadata = GameMetadata("/index/game")))
        val token = key(setOf("games:read"))
        assertEquals(401, request("/v1/games/index").statusCode())
        assertEquals(403, request("/v1/games/index", key(setOf("instance:read"))).statusCode())
        val response = request("/v1/games/index", token)
        assertEquals(200, response.statusCode(), response.body())
        val entries = mapper.readTree(response.body()).toList()
        val expected = games.findAll().sortedBy { it.id }
        assertEquals(expected.map { it.id.toString() }, entries.map { it["id"].asString() })
        assertEquals(expected.map { it.updatedAt!! }, entries.map { Instant.parse(it["updatedAt"].asString()) })
        assertTrue(entries.all { it.propertyNames().toSet() == setOf("id", "updatedAt") })
        assertEquals(200, request("/v1/games/${game.id}", token).statusCode())
        games.deleteById(game.id!!)
        assertFalse(
            mapper.readTree(request("/v1/games/index", token).body()).any { it["id"].asString() == game.id.toString() })
    }

    @Test
    fun `instance and metadata search use public contracts and enforce scopes`() {
        val token = key(setOf("instance:read"))
        val response = request("/v1/instance", token)
        assertEquals(200, response.statusCode(), response.body())
        val instance = mapper.readTree(response.body())
        assertFalse(instance.has("capabilities"))
        assertTrue(instance["features"]["sso"]["enabled"].isBoolean)
        assertTrue(instance["features"]["publicAccess"]["enabled"].isBoolean)
        assertTrue(instance["features"]["gameRequests"]["maxOpenRequestsPerUser"].isIntegralNumber)
        assertEquals(401, request("/v1/metadata/search?q=Game").statusCode())
        assertEquals(403, request("/v1/metadata/search?q=Game", token).statusCode())
        val readToken = key(setOf("games:read"))
        assertEquals(400, request("/v1/metadata/search", readToken).statusCode())
        assertEquals(400, request("/v1/metadata/search?q=%20", readToken).statusCode())
        assertEquals(400, request("/v1/metadata/search?q=Game&platform=invalid", readToken).statusCode())
        assertEquals(200, request("/v1/metadata/search?q=Game", readToken).statusCode())
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
