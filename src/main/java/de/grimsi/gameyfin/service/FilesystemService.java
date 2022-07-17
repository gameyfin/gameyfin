package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Company;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbApiProperties;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class FilesystemService {

    @Value("${gameyfin.root}")
    private String rootFolderPath;

    @Value("${gameyfin.cache}")
    private String cacheFolderPath;

    @Value("${gameyfin.file-extensions}")
    private List<String> possibleGameFileExtensions;

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private UnmappableFileRepository unmappableFileRepository;

    private WebClient igdbImageClient = WebClient.create(IgdbApiProperties.IMAGES_BASE_URL);

    public List<Path> getGameFiles() {

        Path rootFolder = Path.of(rootFolderPath);

        try (Stream<Path> stream = Files.list(rootFolder)) {
            // return all sub-folders (non-recursive) and files that have an extension that indicates that they are a downloadable file
            return stream
                    .filter(p -> Files.isDirectory(p) || possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString())))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Error while opening root folder", e);
        }
    }

    public void scanGameLibrary() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting scan...");
        stopWatch.start();

        AtomicInteger newUnmappedFilesCounter = new AtomicInteger();

        List<Path> gameFiles = getGameFiles();

        // Check if any games that are in the library have been removed from the file system
        // This would include renamed files, but they will be re-detected by the next step
        List<DetectedGame> deletedGames = detectedGameRepository.getAllByPathNotIn(gameFiles);
        detectedGameRepository.deleteAll(deletedGames);
        deletedGames.forEach(g -> log.info("Game '{}' has been moved or deleted.", g.getPath()));

        // Now check if there are any unmapped files that have been removed from the file system
        List<UnmappableFile> deletedUnmappableFiles = unmappableFileRepository.getAllByPathNotIn(gameFiles);
        unmappableFileRepository.deleteAll(deletedUnmappableFiles);
        deletedUnmappableFiles.forEach(g -> log.info("Unmapped file '{}' has been moved or deleted.", g.getPath()));

        // Filter out the games we already know and the ones we already tried to map to a game without success
        gameFiles = gameFiles.stream()
                .filter(g -> !detectedGameRepository.existsByPath(g.toString()))
                .filter(g -> !unmappableFileRepository.existsByPath(g.toString()))
                .peek(p -> log.info("Found new potential game: {}", p))
                .toList();

        // For each new game, load the info from IGDB
        // If a game is not found on IGDB, add it to the list of unmapped files so we won't query the API later on for the same path
        // If a game is not found on IGDB, blacklist the path, so we won't query the API later for the same path
        List<DetectedGame> newDetectedGames = gameFiles.parallelStream()
                .map(p -> {
                    Optional<Igdb.Game> optionalGame = igdbWrapper.searchForGameByTitle(getFilename(p));
                    return optionalGame.map(game -> Map.entry(p, game)).or(() -> {
                        unmappableFileRepository.save(new UnmappableFile(p.toString()));
                        newUnmappedFilesCounter.getAndIncrement();
                        log.info("Added path '{}' to list of unmapped files", p);
                        return Optional.empty();
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .peek(e -> log.info("Mapped file '{}' to game '{}' (slug: {})", e.getKey(), e.getValue().getName(), e.getValue().getSlug()))
                .map(e -> GameMapper.toDetectedGame(e.getValue(), e.getKey()))
                .toList();

        newDetectedGames = detectedGameRepository.saveAll(newDetectedGames);

        stopWatch.stop();

        log.info("Scan finished in {} seconds: Found {} new games, deleted {} games, could not map {} files/folders, {} games total.",
                (int) stopWatch.getTotalTimeSeconds(), newDetectedGames.size(), deletedGames.size() + deletedUnmappableFiles.size(), newUnmappedFilesCounter.get(), detectedGameRepository.count());
    }

    public void downloadGameCovers() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game cover download...");
        stopWatch.start();

        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>(
                detectedGameRepository.findAll().stream()
                        .collect(Collectors.toMap(DetectedGame::getTitle, g -> Collections.singletonList(g.getCoverId()))));

        int downloadCount = downloadImagesIntoCache(multiValueMap, IgdbApiProperties.COVER_IMAGE_SIZE, "cover", "game");

        stopWatch.stop();

        log.info("Downloaded {} covers in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    public void downloadGameScreenshots() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game screenshot download...");
        stopWatch.start();

        MultiValueMap<String, String> gamesToImageIds = new LinkedMultiValueMap<>(
                detectedGameRepository.findAll().stream()
                        .collect(Collectors.toMap(DetectedGame::getTitle, DetectedGame::getScreenshotIds)));

        int downloadCount = downloadImagesIntoCache(gamesToImageIds, IgdbApiProperties.SCREENSHOT_IMAGE_SIZE, "screenshot", "game");

        stopWatch.stop();

        log.info("Downloaded {} screenshots in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    public void downloadCompanyLogos() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting company logo download...");
        stopWatch.start();

        Map<String, List<String>> test = detectedGameRepository.findAll().stream()
                .flatMap(g -> g.getCompanies().stream())
                .collect(Collectors.toMap(Company::getName, c -> Collections.singletonList(c.getLogoId()), (c1, c2) -> c1));

        MultiValueMap<String, String> gamesToImageIds = new LinkedMultiValueMap<>(test);

        int downloadCount = downloadImagesIntoCache(gamesToImageIds, IgdbApiProperties.LOGO_IMAGE_SIZE, "logo", "company");

        stopWatch.stop();

        log.info("Downloaded {} company logos in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    private String getFilename(Path p) {
        return FilenameUtils.getBaseName(p.toString());
    }

    private int downloadImagesIntoCache(MultiValueMap<String, String> entityToImageIds, String imageSize, String imageType, String entityType) {
        AtomicInteger downloadCounter = new AtomicInteger();
        Path cacheFolder = Path.of(cacheFolderPath);

        try {
            Files.createDirectories(cacheFolder);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not create cache folder.");
        }

        entityToImageIds.entrySet().parallelStream().forEach(entry ->
                entry.getValue().forEach(imageId -> {

                    if(!StringUtils.hasText(imageId)) return;

                    String imgFileName = "%s.jpg".formatted(imageId);
                    String imgUrl = "t_%s/%s".formatted(imageSize, imgFileName);

                    if (Files.exists(Path.of(cacheFolderPath, imgFileName))) {
                        log.debug("{} for {} '{}' already downloaded ({}), skipping.",
                                imageType.substring(0, 1).toUpperCase() + imageType.substring(1).toLowerCase(),
                                entityType,
                                entry.getKey(),
                                imgFileName);
                        return;
                    }

                    Flux<DataBuffer> dataBuffer = igdbImageClient.get()
                            .uri(imgUrl)
                            .retrieve()
                            .bodyToFlux(DataBuffer.class);

                    try {
                        DataBufferUtils.write(dataBuffer, cacheFolder.resolve(imgFileName), StandardOpenOption.CREATE)
                                .share().block();
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
