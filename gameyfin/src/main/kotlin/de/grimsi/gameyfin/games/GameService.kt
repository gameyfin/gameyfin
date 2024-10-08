package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataPlugin
import org.pf4j.spring.SpringPluginManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val pluginManager: SpringPluginManager
) {
    val metadataPlugins: List<GameMetadataPlugin>
        get() = pluginManager.getExtensions(GameMetadataPlugin::class.java)

    fun createOrUpdate(game: Game): Game {
        return gameRepository.save(game)
    }

    fun createFromFile(path: Path): Game {
        val metadata = metadataPlugins.first().fetchMetadata(path.fileName.toString())
        val game = Game(
            title = metadata.title,
            summary = metadata.description,
            path = path.toString()
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
            id = game.id,
            title = game.title
        )
    }
}