package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GameService {

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private UnmappableFileRepository unmappableFileRepository;

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
        return detectedGameRepository.findAll().stream().map(GameMapper::toGameOverviewDto).toList();
    }

    public DetectedGame mapUnmappedFile(Long unmappedGameId, String igdbSlug) {

        UnmappableFile unmappableFile = unmappableFileRepository.findById(unmappedGameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unmapped file with id '%d' does not exist.".formatted(unmappedGameId)));

        if(detectedGameRepository.existsBySlug(igdbSlug))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game with slug '%s' already exists in database.".formatted(igdbSlug));

        Igdb.Game igdbGame = igdbWrapper.getGameBySlug(igdbSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with slug '%s' does not exist on IGDB.".formatted(igdbSlug)));

        DetectedGame game = GameMapper.toDetectedGame(igdbGame, Path.of(unmappableFile.getPath()));
        game = detectedGameRepository.save(game);

        unmappableFileRepository.delete(unmappableFile);

        return game;
    }
}
