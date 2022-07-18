package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/unmapped-files")
@RequiredArgsConstructor
public class UnmappedFileController {

    private final GameService gameService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<UnmappableFile> getUnmappedFiles() {
        return gameService.getAllUnmappedFiles();
    }

    @PostMapping(value = "/{unmappedFileId}/map-to/{igdbSlug}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DetectedGame mapGameManually(@PathVariable Long unmappedFileId, @PathVariable String igdbSlug) {
        return gameService.mapUnmappedFile(unmappedFileId, igdbSlug);
    }

}
