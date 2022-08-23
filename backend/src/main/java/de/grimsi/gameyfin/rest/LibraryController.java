package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.ImageDownloadResultDto;
import de.grimsi.gameyfin.dto.LibraryScanResult;
import de.grimsi.gameyfin.dto.LibraryScanResultDto;
import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.ImageService;
import de.grimsi.gameyfin.service.LibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StopWatch;
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
    private final ImageService imageService;

    @GetMapping(value = "/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public LibraryScanResultDto scanLibrary(@RequestParam(value = "download_images", defaultValue = "true") boolean downloadImages) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        LibraryScanResultDto lscDto = new LibraryScanResultDto();

        LibraryScanResult lsc = libraryService.scanGameLibrary();
        lscDto.setNewGames(lsc.getNewGames());
        lscDto.setDeletedGames(lsc.getDeletedGames());
        lscDto.setNewUnmappableFiles(lsc.getNewUnmappableFiles());
        lscDto.setTotalGames(lsc.getTotalGames());

        if(downloadImages) {
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
