package de.grimsi.gameyfin.rest;


import de.grimsi.gameyfin.dto.PathToSlugDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/library-management")
@PreAuthorize("hasAuthority('ADMIN_API_ACCESS')")
@RequiredArgsConstructor
public class LibraryManagementController {

    private final GameService gameService;
    private final DownloadService downloadService;

    @DeleteMapping(value = "/delete-game/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteGame(@PathVariable String slug) {
        gameService.deleteGame(slug);
    }

    @GetMapping(value = "/confirm-game/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame confirmMatch(@PathVariable String slug, @RequestParam(required = false, defaultValue = "true") boolean confirm) {
        return gameService.confirmGame(slug, confirm);
    }

    @PostMapping(value = "/map-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame manuallyMapPathToSlug(@RequestBody PathToSlugDto pathToSlugDto) {
        DetectedGame game = gameService.mapPathToGame(pathToSlugDto.getPath(), pathToSlugDto.getSlug());
        
        downloadService.downloadGameCoversFromIgdb();
        downloadService.downloadGameScreenshotsFromIgdb();
        downloadService.downloadCompanyLogosFromIgdb();

        return game;
    }

    @GetMapping(value = "/unmapped-files", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UnmappableFile> getUnmappedFiles() {
        return gameService.getAllUnmappedFiles();
    }
}
