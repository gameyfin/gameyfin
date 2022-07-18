package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.service.FilesystemService;
import de.grimsi.gameyfin.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
public class GameyfinDevController {

    @Autowired
    private FilesystemService filesystemService;

    @Autowired
    private GameService gameService;

    @GetMapping(value = "/dev/gameFiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getAllGameFiles() {
        return filesystemService.getGameFiles().stream().map(Path::toString).toList();
    }

    @GetMapping(value = "/dev/games", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DetectedGame> getAllGames() {
        return gameService.getAllDetectedGames();
    }

    @GetMapping(value = "/dev/images/{imageId}", produces = MediaType.IMAGE_PNG_VALUE)
    public Resource getCoverImageForGame(@PathVariable String imageId) {
        return filesystemService.getImage(imageId);
    }

    @GetMapping(value = "/dev/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public void scanLibrary() {
        filesystemService.scanGameLibrary();
    }

    @GetMapping(value = "/dev/cache/download")
    public void downloadCovers() {
        filesystemService.downloadGameCovers();
        filesystemService.downloadGameScreenshots();
        filesystemService.downloadCompanyLogos();
    }


    @GetMapping(value = "/dev/unmappedFiles", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UnmappableFile> getUnmappedFiles() {
        return gameService.getAllUnmappedFiles();
    }

    @GetMapping(value = "/dev/gameMappings", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> getGameMappings() {
        return gameService.getAllMappings();
    }

    @PostMapping(value = "/dev/unmappedFiles/{unmappedGameId}/mapTo/{igdbSlug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame mapGameManually(@PathVariable Long unmappedGameId, @PathVariable String igdbSlug) {
        return gameService.mapUnmappedFile(unmappedGameId, igdbSlug);
    }

}
