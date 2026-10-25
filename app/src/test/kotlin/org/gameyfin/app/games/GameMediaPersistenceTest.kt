package org.gameyfin.app.games

import io.mockk.*
import liquibase.integration.spring.SpringLiquibase
import org.gameyfin.app.core.sync.EntityChangeProcessor
import org.gameyfin.app.core.sync.EntityChangeSet
import org.gameyfin.app.core.sync.EntityChangeTracker
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageType
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypesScanner
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameMediaPersistenceTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `migration preserves media and reordered lists survive new sessions`(legacyIndex: Boolean) {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:media-${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        migrate(dataSource)
        val factory = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            val entityTypes = PersistenceManagedTypesScanner(DefaultResourceLoader()).scan("org.gameyfin.app")
                .managedClassNames.filterNot { it == CompanyScanRecord::class.java.name }
            setManagedTypes(PersistenceManagedTypes.of(entityTypes, emptyList()))
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setJpaPropertyMap(mapOf(
                "hibernate.hbm2ddl.auto" to "validate",
                "hibernate.implicit_naming_strategy" to "org.springframework.boot.hibernate.SpringImplicitNamingStrategy",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"
            ))
            afterPropertiesSet()
        }
        try {
            val emf = factory.`object`!!
            val longVideo = URI("https://example.com/" + "long-path/".repeat(100) + "video.mp4?token=" + "a".repeat(500))
            val originalVideos = listOf(URI("https://example.com/b.mp4"), URI("https://example.com/a.mp4"), URI("https://example.com/a.mp4"), longVideo)
            var gameId = 0L
            var imageIds = emptyList<Long>()
            emf.createEntityManager().use { em ->
                em.transaction.begin()
                val library = Library(name = "Media test")
                em.persist(library)
                val images = (1..3).map { Image(type = ImageType.SCREENSHOT).also(em::persist) }
                val game = Game(Path.of("/games/media-test"), library).apply {
                    title = "Media test"
                    this.images = images.reversed().toMutableList()
                    videoUrls = originalVideos
                }
                em.persist(game)
                em.transaction.commit()
                gameId = game.id!!
                imageIds = images.map { it.id!! }
            }

            // Recreate the pre-upgrade join tables, including duplicate video URLs.
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("CREATE TABLE GAME_IMAGES (GAME_ID BIGINT NOT NULL, IMAGES_ID BIGINT NOT NULL)")
                    statement.execute("CREATE TABLE GAME_VIDEO_URLS (GAME_ID BIGINT NOT NULL, VIDEO_URLS VARBINARY(8192))")
                    statement.execute("INSERT INTO GAME_IMAGES SELECT GAME_ID, IMAGE_ID FROM GAME_MEDIA WHERE IMAGE_ID IS NOT NULL")
                    statement.execute("INSERT INTO GAME_VIDEO_URLS SELECT GAME_ID, VIDEO_URL FROM GAME_MEDIA WHERE VIDEO_URL IS NOT NULL")
                    statement.execute("DROP TABLE GAME_MEDIA")
                    statement.execute(if (legacyIndex) "CREATE UNIQUE INDEX UX_GAME_IMAGES_GAME_IMAGE ON GAME_IMAGES (GAME_ID, IMAGES_ID)"
                        else "ALTER TABLE GAME_IMAGES ADD CONSTRAINT UX_GAME_IMAGES_GAME_IMAGE UNIQUE (GAME_ID, IMAGES_ID)")
                    statement.execute("DELETE FROM DATABASECHANGELOG WHERE ID = '2.6.0-game-media-order'")
                }
            }
            migrate(dataSource)

            val changes = mutableListOf<EntityChangeSet>()
            val processor = mockk<EntityChangeProcessor>()
            every { processor.enqueue(capture(changes)) } just Runs
            EntityChangeTracker(emf, processor).register()
            val transaction = TransactionTemplate(JpaTransactionManager(emf))
            transaction.executeWithoutResult {
                val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
                val game = em.find(Game::class.java, gameId)
                assertEquals(imageIds, game.images.map { it.id })
                assertEquals(originalVideos.groupingBy { it }.eachCount(), game.videoUrls.groupingBy { it }.eachCount())
                assertEquals(List(3) { "image" } + List(4) { "video" }, game.mediaOrder)
                game.images = listOf(game.images[2], game.images[0], game.images[1]).toMutableList()
                game.videoUrls = listOf(longVideo, URI("https://example.com/new.mp4"), originalVideos[0])
                game.mediaOrder = listOf("image", "video", "image", "image", "video", "video")
            }
            val change = EntityChangeSet.merge(changes).games.getValue(gameId)
            assertTrue(GameField.IMAGES in change.fields)
            assertTrue(GameField.VIDEO_URLS in change.fields)
            assertTrue(GameField.MEDIA_ORDER in change.fields)
            assertTrue(change.rowUpdated || change.requiresTimestampBump)
            emf.createEntityManager().use { em ->
                em.transaction.begin()
                val game = em.find(Game::class.java, gameId)
                assertEquals(listOf(imageIds[2], imageIds[0], imageIds[1]), game.images.map { it.id })
                assertEquals(listOf(longVideo, URI("https://example.com/new.mp4"), originalVideos[0]), game.videoUrls)
                assertEquals(listOf("image", "video", "image", "image", "video", "video"), game.mediaOrder)
                val repository = JpaRepositoryFactory(em).getRepository(GameRepository::class.java)
                assertTrue(repository.existsByImage(imageIds[0]))
                assertEquals(listOf(gameId), repository.findAllByImage(imageIds[0]).map { it.id })
                assertEquals(listOf(gameId), repository.findIdsByGalleryImageIds(imageIds))
                em.transaction.commit()
            }

            // Changing only the shared order must persist and notify all derived gallery fields.
            changes.clear()
            val videoFirst = listOf("video", "image", "video", "image", "video", "image")
            transaction.executeWithoutResult {
                val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
                em.find(Game::class.java, gameId).mediaOrder = videoFirst
            }
            assertTrue(EntityChangeSet.merge(changes).games.getValue(gameId).fields.containsAll(
                listOf(GameField.IMAGES, GameField.VIDEO_URLS, GameField.MEDIA_ORDER)
            ))
            emf.createEntityManager().use { em ->
                em.transaction.begin()
                val game = em.find(Game::class.java, gameId)
                assertEquals(videoFirst, game.mediaOrder)
                assertEquals(listOf(imageIds[2], imageIds[0], imageIds[1]), game.images.map { it.id })
                assertEquals(listOf(longVideo, URI("https://example.com/new.mp4"), originalVideos[0]), game.videoUrls)
                // Recovery code mutates the images view directly.
                assertTrue(game.images.removeIf { it.id == imageIds[0] })
                assertEquals(listOf("video", "image", "video", "video", "image"), game.mediaOrder)
                game.images.clear()
                assertEquals(List(3) { "video" }, game.mediaOrder)
                game.videoUrls = emptyList()
                em.transaction.commit()
            }
            emf.createEntityManager().use { em ->
                em.transaction.begin()
                val game = em.find(Game::class.java, gameId)
                assertTrue(game.images.isEmpty())
                assertTrue(game.videoUrls.isEmpty())
                assertTrue(game.media.isEmpty())
                em.transaction.commit()
            }
        } finally {
            factory.destroy()
        }
    }

    private fun migrate(source: DataSource) {
        SpringLiquibase().apply {
            dataSource = source
            changeLog = "classpath:/db/changelog/db.changelog-master.yaml"
            resourceLoader = DefaultResourceLoader()
            afterPropertiesSet()
        }
    }
}
