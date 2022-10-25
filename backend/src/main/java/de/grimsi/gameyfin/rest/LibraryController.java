package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.*;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.service.ImageService;
import de.grimsi.gameyfin.service.LibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
    private final ImageService imageService;

    @PostMapping(value = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public LibraryScanResultDto scanLibraries(@RequestBody LibraryScanRequestDto libraryScanRequest) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        LibraryScanResultDto lscDto = new LibraryScanResultDto();

        String path = libraryScanRequest.getPath();
        List<Library> libraries = isNotBlank(path) ? List.of(libraryService.getLibrary(path)) : libraryService.getLibraries();
        List<LibraryScanResult> libraryScanResults = libraries.stream().map(libraryService::scanGameLibrary).toList();

        lscDto.setNewGames(libraryScanResults.stream().map(LibraryScanResult::getNewGames).reduce(0, Integer::sum));
        lscDto.setDeletedGames(libraryScanResults.stream().map(LibraryScanResult::getDeletedGames).reduce(0, Integer::sum));
        lscDto.setNewUnmappableFiles(libraryScanResults.stream().map(LibraryScanResult::getNewUnmappableFiles).reduce(0, Integer::sum));
        lscDto.setTotalGames(libraryScanResults.stream().map(LibraryScanResult::getTotalGames).reduce(0, Integer::sum));

        if (libraryScanRequest.isDownloadImages()) {
            ImageDownloadResultDto idrDto = downloadImages();

            lscDto.setCoverDownloads(idrDto.getCoverDownloads());
            lscDto.setScreenshotDownloads(idrDto.getScreenshotDownloads());
            lscDto.setCompanyLogoDownloads(idrDto.getCompanyLogoDownloads());
        }

        stopWatch.stop();
        lscDto.setScanDuration((int) stopWatch.getTotalTimeSeconds());

        log.info("Library scan completed in {} seconds.", (int) stopWatch.getTotalTimeSeconds());

        return lscDto;
    }

    @GetMapping(value = "/download-images")
    public ImageDownloadResultDto downloadImages() {
        ImageDownloadResultDto idrDto = new ImageDownloadResultDto();

        idrDto.setCoverDownloads(imageService.downloadGameCoversFromIgdb());
        idrDto.setScreenshotDownloads(imageService.downloadGameScreenshotsFromIgdb());
        idrDto.setCompanyLogoDownloads(imageService.downloadCompanyLogosFromIgdb());

        log.info("Downloading images completed.");

        return idrDto;
    }

    @GetMapping(value = "/files", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<String> getAllFiles() {
        return libraryService.getGameFiles().stream().map(Path::toString).toList();
    }
}
