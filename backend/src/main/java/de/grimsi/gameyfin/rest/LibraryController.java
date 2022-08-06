package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.LibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.List;

/**
 * This controller handles functionality of the library.
 */
@RestController
@RequestMapping("/v1/library")
@PreAuthorize("hasAuthority('ADMIN_API_ACCESS')")
@RequiredArgsConstructor
@Slf4j
public class LibraryController {

    private final LibraryService libraryService;
    private final DownloadService downloadService;

    @GetMapping(value = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public void scanLibrary(@RequestParam(value = "download_images", defaultValue = "true") boolean downloadImages) {
        libraryService.scanGameLibrary();

        if(downloadImages) downloadImages();
    }

    @GetMapping(value = "/download-images")
    public void downloadImages() {
        downloadService.downloadGameCoversFromIgdb();
        downloadService.downloadGameScreenshotsFromIgdb();
        downloadService.downloadCompanyLogosFromIgdb();

        log.info("Downloading images completed.");
    }

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getAllFiles() {
        return libraryService.getGameFiles().stream().map(Path::toString).toList();
    }
}
