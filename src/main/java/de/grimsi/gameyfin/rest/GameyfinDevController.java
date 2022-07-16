package de.grimsi.gameyfin.rest;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.service.FilesystemService;
import de.grimsi.gameyfin.service.GameService;
import de.grimsi.gameyfin.util.ProtobufUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@RestController
public class GameyfinDevController {

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private FilesystemService filesystemService;

    @Autowired
    private GameService gameService;

    @GetMapping(value = "/dev/findGameByTitle/{title}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GameDto findGameByTitle(@PathVariable("title") String title) {
        Igdb.Game game = igdbWrapper.searchForGameByTitle(title)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find game with title: \"%s\"".formatted(title)));

        return GameDto.builder()
                .name(game.getName())
                .releaseDate(ProtobufUtils.toInstant(game.getFirstReleaseDate()))
                .build();
    }

    @GetMapping(value = "/dev/getGameById/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GameDto findGameByTitle(@PathVariable("id") Long id) {
        Igdb.Game game = igdbWrapper.getGameById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find game with id: %d".formatted(id)));

        return GameDto.builder()
                .name(game.getName())
                .releaseDate(ProtobufUtils.toInstant(game.getFirstReleaseDate()))
                .build();
    }

    @GetMapping(value = "/dev/gameFiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getAllGameFiles() {
        return filesystemService.getGameFiles().stream().map(Path::toString).toList();
    }

    @GetMapping(value = "/dev/games", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DetectedGame> getAllGames() {
        return gameService.getAllDetectedGames();
    }

    @GetMapping(value = "/dev/startScan", produces = MediaType.APPLICATION_JSON_VALUE)
    public void scanLibrary() {
        filesystemService.scanGameLibrary();
    }

    @GetMapping(value = "/dev/unmappedFiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UnmappableFile> getUnmappedFiles() {
        return gameService.getAllUnmappedFiles();
    }

}
