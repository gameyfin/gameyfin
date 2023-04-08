package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.Company;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.igdb.IgdbApiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class ImageService {

    private final FilesystemService filesystemService;
    private final GameService gameService;
    private final WebClient.Builder webclientBuilder;
    private WebClient igdbImageClient;

    @PostConstruct
    public void init() {
        igdbImageClient = webclientBuilder.baseUrl(IgdbApiProperties.IMAGES_BASE_URL).build();
    }

    public int downloadGameCoversFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game cover download...");
        stopWatch.start();

        MultiValueMap<String, String> gameToImageIds = new LinkedMultiValueMap<>(
                gameService.getAllDetectedGames().stream()
                        .collect(Collectors.toMap(DetectedGame::getSlug, g -> Collections.singletonList(g.getCoverId()))));

        int downloadCount = saveImagesIntoCache(gameToImageIds, IgdbApiProperties.COVER_IMAGE_SIZE, "cover", "game");

        stopWatch.stop();

        log.info("Downloaded {} covers in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
        return downloadCount;
    }

    public int downloadGameScreenshotsFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game screenshot download...");
        stopWatch.start();

        MultiValueMap<String, String> gamesToImageIds = new LinkedMultiValueMap<>(
                gameService.getAllDetectedGames().stream()
                        .collect(Collectors.toMap(DetectedGame::getSlug, DetectedGame::getScreenshotIds)));

        int downloadCount = saveImagesIntoCache(gamesToImageIds, IgdbApiProperties.SCREENSHOT_IMAGE_SIZE, "screenshot", "game");

        stopWatch.stop();

        log.info("Downloaded {} screenshots in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
        return downloadCount;
    }

    public int downloadCompanyLogosFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting company logo download...");
        stopWatch.start();

        Map<String, List<String>> companyToLogoIdMap = gameService.getAllDetectedGames().stream()
                .flatMap(g -> g.getCompanies().stream())
                .collect(Collectors.toMap(Company::getSlug, c -> Collections.singletonList(c.getLogoId()), (c1, c2) -> c1));

        MultiValueMap<String, String> companiesToLogoIds = new LinkedMultiValueMap<>(companyToLogoIdMap);

        int downloadCount = saveImagesIntoCache(companiesToLogoIds, IgdbApiProperties.LOGO_IMAGE_SIZE, "logo", "company");

        stopWatch.stop();

        log.info("Downloaded {} company logos in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
        return downloadCount;
    }

    private int saveImagesIntoCache(MultiValueMap<String, String> entityToImageIds, String imageSize, String imageType, String entityType) {
        AtomicInteger downloadCounter = new AtomicInteger();

        entityToImageIds.entrySet().parallelStream().forEach(entry ->
                entry.getValue().forEach(imageId -> {

                    if (!StringUtils.hasText(imageId)) return;

                    String imgFileName = "%s.png".formatted(imageId);
                    String imgUrl = "t_%s/%s".formatted(imageSize, imgFileName);

                    if (filesystemService.doesCachedFileExist(imgFileName)) {
                        if (filesystemService.isCachedFileCorrupt(imgFileName)) {
                            log.info("File '{}' is corrupt, retrying download...", imgFileName);
                            filesystemService.deleteFileFromCache(imgFileName);
                        } else {
                            log.debug("{} for {} '{}' already downloaded ({}), skipping.",
                                    imageType.substring(0, 1).toUpperCase() + imageType.substring(1).toLowerCase(),
                                    entityType,
                                    entry.getKey(),
                                    imgFileName);
                            return;
                        }
                    }

                    Flux<DataBuffer> dataBuffer = igdbImageClient.get()
                            .uri(imgUrl)
                            .retrieve()
                            .bodyToFlux(DataBuffer.class);

                    try {
                        filesystemService.saveFileToCache(dataBuffer, imgFileName);
                    } catch (WebClientResponseException e) {
                        if (e.getStatusCode().is4xxClientError()) {
                            log.error("Could not download {} for {} '{}' from {}: {}", imageType, entityType, entry.getKey(), IgdbApiProperties.IMAGES_BASE_URL + imgUrl, e.getStatusCode());
                        }
                    }

                    downloadCounter.getAndIncrement();
                    log.info("Downloaded {} for {} '{}' from {}", imageType, entityType, entry.getKey(), IgdbApiProperties.IMAGES_BASE_URL + imgUrl);
                }));

        return downloadCounter.get();
    }
}
