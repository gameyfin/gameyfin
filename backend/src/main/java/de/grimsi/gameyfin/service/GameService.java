package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import de.grimsi.gameyfin.repositories.LibraryRepository;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class GameService {

    private final IgdbWrapper igdbWrapper;
    private final GameMapper gameMapper;
    private final DetectedGameRepository detectedGameRepository;
    private final UnmappableFileRepository unmappableFileRepository;

    private final LibraryRepository libraryRepository;

    public List<DetectedGame> getAllDetectedGames() {
        return detectedGameRepository.findAll();
    }

    public DetectedGame getDetectedGame(String slug) {
        return detectedGameRepository.findById(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with slug '%s' not found in library.".formatted(slug)));
    }

    public List<UnmappableFile> getAllUnmappedFiles() {
        return unmappableFileRepository.findAll();
    }

    public Map<String, String> getAllMappings() {
        return detectedGameRepository.findAll().stream().collect(Collectors.toMap(DetectedGame::getPath, DetectedGame::getTitle));
    }

    public List<GameOverviewDto> getGameOverviews() {
        return detectedGameRepository.findAll().stream().map(gameMapper::toGameOverviewDto).toList();
    }

    public void deleteGame(String slug) {
        DetectedGame gameToBeDeleted = getDetectedGame(slug);

        // Add the path of the game to be deleted to the unmappable files,
        // so it doesn't get re-indexed on the next library scan
        unmappableFileRepository.save(new UnmappableFile(gameToBeDeleted.getPath()));

        detectedGameRepository.deleteById(slug);
    }

    public void deleteUnmappedFile(Long id) {
        unmappableFileRepository.deleteById(id);
    }

    public DetectedGame confirmGame(String slug, boolean confirm) {
        DetectedGame g = getDetectedGame(slug);
        g.setConfirmedMatch(confirm);
        return detectedGameRepository.save(g);
    }

    public DetectedGame mapPathToGame(String path, String slug) {

        if (detectedGameRepository.existsBySlug(slug))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game with slug '%s' already exists in database.".formatted(slug));

        Optional<UnmappableFile> optionalUnmappableFile = unmappableFileRepository.findByPath(path);
        Optional<DetectedGame> optionalDetectedGame = detectedGameRepository.findByPath(path);

        if (optionalUnmappableFile.isPresent()) {
            return mapUnmappableFile(optionalUnmappableFile.get(), slug);
        }

        if (optionalDetectedGame.isPresent()) {
            return mapDetectedGame(optionalDetectedGame.get(), slug);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Path '%s' not in database".formatted(path));
    }

    public DetectedGame refreshGame(String slug) {
        Optional<DetectedGame> optionalDetectedGame = detectedGameRepository.findById(slug);

        if (optionalDetectedGame.isPresent()) {
            log.info("Refreshing game with slug '{}'", slug);
            return mapDetectedGame(optionalDetectedGame.get(), slug);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with slug '%s' not found in database".formatted(slug));
    }

    private DetectedGame mapUnmappableFile(UnmappableFile unmappableFile, String slug) {
        Igdb.Game igdbGame = igdbWrapper.getGameBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with slug '%s' does not exist on IGDB.".formatted(slug)));

        Path path = Path.of(unmappableFile.getPath());
        // parent file should equal to the library
        File libraryPath = path.toFile().getParentFile();
        Library library = libraryRepository.findByPath(libraryPath.toString()).orElse(null);
        DetectedGame game = gameMapper.toDetectedGame(igdbGame, path, library);
        game.setConfirmedMatch(true);

        game = detectedGameRepository.save(game);

        unmappableFileRepository.delete(unmappableFile);

        return game;
    }

    private DetectedGame mapDetectedGame(DetectedGame existingGame, String slug) {
        Igdb.Game igdbGame = igdbWrapper.getGameBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with slug '%s' does not exist on IGDB.".formatted(slug)));

        Path path = Path.of(existingGame.getPath());
        // parent file should equal to the library
        File libraryPath = path.toFile().getParentFile();
        Library library = libraryRepository.findByPath(libraryPath.toString()).orElse(null);
        DetectedGame game = gameMapper.toDetectedGame(igdbGame, path, library);
        game.setConfirmedMatch(true);

        return detectedGameRepository.save(game);
    }
}
