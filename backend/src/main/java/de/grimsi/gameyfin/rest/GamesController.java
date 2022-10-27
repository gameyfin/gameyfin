package de.grimsi.gameyfin.rest;

import bt.torrent.maker.TorrentBuilder;
import com.turn.ttorrent.common.creation.MetadataBuilder;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.GameService;
import de.grimsi.gameyfin.service.TorrentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This controller handles logic related to detected games.
 */
@RestController
@RequestMapping("/v1/games")
@RequiredArgsConstructor
public class GamesController {

    private final GameService gameService;
    private final DownloadService downloadService;

    @Value("${gameyfin.torrent}")
    private String torrentFolderPath;

    @Value("${gameyfin.trackerhostname}")
    private String trackerHostname;

    @Value("${gameyfin.trackerport}")
    private String trackerPort;

    @Value("${gameyfin.trackerssl}")
    private boolean trackerSSL;

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

    @GetMapping(value = "/game/{slug}/torrent")
    public ResponseEntity<StreamingResponseBody> downloadGameTorrent(@PathVariable String slug) {
        if (!TorrentService.isTrackerEnabled()) {
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");
            return (ResponseEntity<StreamingResponseBody>) ResponseEntity
                    //TODO what to return here??
                    .status(501)
                    .headers(headers);
        }

        DetectedGame game = gameService.getDetectedGame(slug);

        String downloadFileName = downloadService.getTorrentFileName(game);

        Path torrentPath = Path.of(torrentFolderPath + "/" + downloadFileName);

        try {
            Path torrentRoot = Paths.get(game.getPath());
            TorrentBuilder torrentBuilder = new TorrentBuilder()
                    .rootPath(torrentRoot);

            List<Path> gameContent = Files.walk(Path.of(game.getPath())).collect(Collectors.toList());

            for (Path p : gameContent) {
                torrentBuilder.addFile(p);
            }

            String announceURL = "";
            if (trackerHostname.equals("localhost")) {
                announceURL = TorrentService.getAnnounceURL();
            } else {
                announceURL = (trackerSSL ? "https://" : "http://") + trackerHostname + ":" + trackerPort + "/announce";
            }

            System.out.println(announceURL);

            byte[] torrentMetadataBytes = torrentBuilder
                    .announce((announceURL))
                    .build();
            FileUtils.writeByteArrayToFile(torrentPath.toFile(), torrentMetadataBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        long downloadFileSize = 0;
        try {
            downloadFileSize = Files.size(torrentPath);
        } catch (IOException e) {

        }

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
                .body(out -> downloadService.sendTorrentToClient(game, torrentPath, out));
    }

    @GetMapping(value = "/game/{slug}/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame refreshGame(@PathVariable String slug) {
        return gameService.refreshGame(slug);
    }

}
