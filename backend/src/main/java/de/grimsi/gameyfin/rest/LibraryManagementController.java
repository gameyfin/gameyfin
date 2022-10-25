package de.grimsi.gameyfin.rest;


import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.dto.PathToSlugDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.entities.Platform;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.service.GameService;
import de.grimsi.gameyfin.service.ImageService;
import de.grimsi.gameyfin.service.LibraryService;
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
    private final ImageService imageService;
    private final LibraryService libraryService;

    @DeleteMapping(value = "/delete-game/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteGame(@PathVariable String slug) {
        gameService.deleteGame(slug);
    }

    @DeleteMapping(value = "/delete-unmapped-file/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public void deleteUnmappedFile(@PathVariable Long id) {
        gameService.deleteUnmappedFile(id);
    }

    @GetMapping(value = "/confirm-game/{slug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame confirmMatch(@PathVariable String slug, @RequestParam(required = false, defaultValue = "true") boolean confirm) {
        return gameService.confirmGame(slug, confirm);
    }

    @PostMapping(value = "/map-path", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame manuallyMapPathToSlug(@RequestBody PathToSlugDto pathToSlugDto) {
        DetectedGame game = gameService.mapPathToGame(pathToSlugDto.getPath(), pathToSlugDto.getSlug());

        imageService.downloadGameCoversFromIgdb();
        imageService.downloadGameScreenshotsFromIgdb();
        imageService.downloadCompanyLogosFromIgdb();

        return game;
    }

    @GetMapping(value = "/unmapped-files", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UnmappableFile> getUnmappedFiles() {
        return gameService.getAllUnmappedFiles();
    }

    @GetMapping(value = "/autocomplete-suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<AutocompleteSuggestionDto> getAutocompleteSuggestions(@RequestParam String searchTerm, @RequestParam(required = false, defaultValue = "10") int limit) {
        return libraryService.getAutocompleteSuggestions(searchTerm, limit);
    }

    @GetMapping(value = "/platforms", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Platform> getPlatforms(@RequestParam String searchTerm, @RequestParam(required = false, defaultValue = "10") int limit) {
        return libraryService.getPlatforms(searchTerm, limit);
    }

    @GetMapping(value = "/libraries", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Library> getLibraries() {
        return libraryService.getOrCreateLibraries();
    }

    @PostMapping(value = "/map-library", produces = MediaType.APPLICATION_JSON_VALUE)
    public Library mapPathToPlatform(@RequestBody PathToSlugDto pathToSlugDto) {
        return libraryService.mapPlatformsToLibrary(pathToSlugDto.getPath(), pathToSlugDto.getSlug());
    }

}
