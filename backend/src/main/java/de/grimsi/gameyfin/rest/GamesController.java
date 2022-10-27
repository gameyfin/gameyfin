package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.Map;

/**
 * This controller handles logic related to detected games.
 */
@RestController
@RequestMapping("/v1/games")
@RequiredArgsConstructor
public class GamesController {

    private final GameService gameService;
    private final DownloadService downloadService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DetectedGame> getAllGames() {
        return gameService.getAllDetectedGames();
    }

    @GetMapping(value = "/game/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame getGame(@PathVariable String slug) {
        return gameService.getDetectedGame(slug);
    }

    @GetMapping(value = "/game-overviews", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<GameOverviewDto> getGameOverviews() {
        return gameService.getGameOverviews();
    }

    @GetMapping(value = "/game-mappings", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getGameMappings() {
        return gameService.getAllMappings();
    }

    @GetMapping(value = "/game/{slug}/download")
    public ResponseEntity<StreamingResponseBody> downloadGameFiles(@PathVariable String slug) {

        DetectedGame game = gameService.getDetectedGame(slug);

        String downloadFileName = downloadService.getDownloadFileName(game);
        long downloadFileSize = downloadService.getDownloadFileSize(game);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(downloadFileName));
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add(HttpHeaders.EXPIRES, "0");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        if (downloadFileSize > 0) {
            headers.setContentLength(downloadFileSize);
        }

        return ResponseEntity
                .ok()
                .headers(headers)
                .body(out -> downloadService.sendGamefilesToClient(game, out));
    }

    @GetMapping(value = "/game/{slug}/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame refreshGame(@PathVariable String slug) {
        return gameService.refreshGame(slug);
    }

}
