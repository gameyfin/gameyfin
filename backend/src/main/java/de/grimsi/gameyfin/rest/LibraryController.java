package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.service.FilesystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
@RequiredArgsConstructor
public class LibraryController {

    private final FilesystemService filesystemService;

    @GetMapping(value = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public void scanLibrary(@RequestParam(value = "download_images", defaultValue = "true") boolean downloadImages) {
        filesystemService.scanGameLibrary();

        if(downloadImages) downloadImages();
    }

    @GetMapping(value = "/download-images")
    public void downloadImages() {
        filesystemService.downloadGameCovers();
        filesystemService.downloadGameScreenshots();
        filesystemService.downloadCompanyLogos();
    }

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getAllFiles() {
        return filesystemService.getGameFiles().stream().map(Path::toString).toList();
    }
}
