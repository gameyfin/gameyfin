package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * This controller handles logic related to detected games.
 */
@RestController
@RequestMapping("/games")
@RequiredArgsConstructor
public class GamesController {

    private final GameService gameService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DetectedGame> getAllGames() {
        return gameService.getAllDetectedGames();
    }

    @GetMapping(value = "/game-mappings", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getGameMappings() {
        return gameService.getAllMappings();
    }
}
