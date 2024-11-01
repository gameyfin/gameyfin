package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.pf4j.PluginManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val pluginManager: PluginManager
) {
    private val log = KotlinLogging.logger {}

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)

    fun createOrUpdate(game: Game): Game {
        gameRepository.findByPath(game.path)?.let {
            game.id = it.id
        }
        return gameRepository.save(game)
    }

    fun createFromFile(path: Path): Game {
        val metadataResults: Map<GameMetadataProvider, GameMetadata?> = runBlocking {
            coroutineScope {
                metadataPlugins.associateWith {
                    async {
                        try {
                            it.fetchMetadata(path.fileName.toString())
                        } catch (e: Exception) {
                            log.error(e) { "Error fetching metadata with plugin ${it.javaClass.name}" }
                            null
                        }
                    }.await()
                }
            }
        }

        val (plugin, metadata) = metadataResults.entries.firstOrNull { it.value != null }
            ?: throw NoMatchException("Could not match game at $path")

        val game = Game(
            title = metadata!!.title,
            summary = metadata.description,
            release = metadata.release,
            publishers = metadata.publishedBy,
            developers = metadata.developedBy,
            path = path.toString(),
            source = plugin.javaClass.name
        )

        return createOrUpdate(game)
    }

    fun getAllGames(): Collection<GameDto> {
        val entities = gameRepository.findAll()
        return entities.map { toDto(it) }
    }

    fun delete(game: Game) {
        gameRepository.delete(game)
    }

    private fun getById(id: Long): Game {
        return gameRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Game with id $id not found")
    }

    private fun toDto(game: Game): GameDto {
        if (game.id == null) {
            throw IllegalArgumentException("Game ID is null")
        }

        return GameDto(
            id = game.id!!,
            title = game.title
        )
    }
}