package de.grimsi.gameyfin.rest;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameDto;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.service.FilesystemService;
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
import java.util.Optional;

@RestController
public class GameyfinDevController {

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private FilesystemService filesystemService;

    @GetMapping(value = "/dev/findGameByTitle/{title}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GameDto findGameByTitle(@PathVariable("title") String title) {
        Igdb.Game game;

        try {
            game = igdbWrapper.findGameByTitle(title);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        return GameDto.builder()
                .name(game.getName())
                .releaseDate(ProtobufUtils.toInstant(game.getFirstReleaseDate()))
                .build();
    }

    @GetMapping(value = "/dev/getGameById/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GameDto findGameByTitle(@PathVariable("id") Long id) {
        Optional<Igdb.Game> gameOptional;

        try {
            gameOptional = igdbWrapper.getGameById(id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        Igdb.Game game = gameOptional.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Game with id %d not found".formatted(id)));

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
    public List<GameDto> getAllGames() {
        return filesystemService.getGameFileNames().stream()
                .map(t -> igdbWrapper.findGameByTitle(t))
                .map(g -> GameDto.builder()
                        .name(g.getName())
                        .releaseDate(ProtobufUtils.toInstant(g.getFirstReleaseDate()))
                        .build())
                .toList();
    }
}
