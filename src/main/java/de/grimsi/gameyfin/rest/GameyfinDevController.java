package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.GameDto;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.igdb.dto.IgdbGame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class GameyfinController {

    @Autowired
    IgdbWrapper igdbWrapper;

    @GetMapping(value = "/findGameByTitle/{title}", produces = MediaType.APPLICATION_JSON_VALUE)
    public GameDto findGameByTitle(@PathVariable("title") String title) {
        IgdbGame game;

        try {
            game = igdbWrapper.findGameByTitle(title);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        return GameDto.builder().name(game.getName()).releaseDate(game.getFirstReleaseDate()).build();
    }
}
