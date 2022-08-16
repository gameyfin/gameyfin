package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.Company;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.exceptions.DownloadAbortedException;
import de.grimsi.gameyfin.igdb.IgdbApiProperties;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static de.grimsi.gameyfin.util.FilenameUtil.getFilenameWithExtension;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadService {

    @Value("${gameyfin.cache}")
    private String cacheFolderPath;

    private final DetectedGameRepository detectedGameRepository;

    @Autowired
    private WebClient.Builder webclientBuilder;
    private WebClient igdbImageClient;

    @PostConstruct
    public void init() {
        igdbImageClient = webclientBuilder.baseUrl(IgdbApiProperties.IMAGES_BASE_URL).build();
    }

    public String getDownloadFileName(DetectedGame g) {
        Path path = Path.of(g.getPath());

        if (!path.toFile().isDirectory()) return getFilenameWithExtension(path);
        return getFilenameWithExtension(path) + ".zip";
    }

    public Resource downloadImage(String imageId) {
        String filename = "%s.png".formatted(imageId);

        try {
            return new ByteArrayResource(Files.readAllBytes(Paths.get("%s/%s".formatted(cacheFolderPath, filename))));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find image file %s".formatted(filename));
        }
    }

    public void downloadGameFiles(DetectedGame game, OutputStream outputStream) {

        StopWatch stopWatch = new StopWatch();

        log.info("Starting game file download for {}...", game.getTitle());

        stopWatch.start();

        Path path = Path.of(game.getPath());

        if (path.toFile().isDirectory()) {

            try {
                downloadFilesAsZip(path, outputStream);
            } catch(DownloadAbortedException e) {
                stopWatch.stop();
                log.info("Download of game {} was aborted by client after {} seconds", game.getTitle(), (int) stopWatch.getTotalTimeSeconds());
                return;
            }

        } else {
            downloadFile(path, outputStream);
        }

        stopWatch.stop();

        log.info("Downloaded game files of {} in {} seconds.", game.getTitle(), (int) stopWatch.getTotalTimeSeconds());
    }


    public void downloadGameCoversFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game cover download...");
        stopWatch.start();

        MultiValueMap<String, String> gameToImageIds = new LinkedMultiValueMap<>(
                detectedGameRepository.findAll().stream()
                        .collect(Collectors.toMap(DetectedGame::getSlug, g -> Collections.singletonList(g.getCoverId()))));

        int downloadCount = downloadImagesIntoCache(gameToImageIds, IgdbApiProperties.COVER_IMAGE_SIZE, "cover", "game");

        stopWatch.stop();

        log.info("Downloaded {} covers in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    public void downloadGameScreenshotsFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting game screenshot download...");
        stopWatch.start();

        MultiValueMap<String, String> gamesToImageIds = new LinkedMultiValueMap<>(
                detectedGameRepository.findAll().stream()
                        .collect(Collectors.toMap(DetectedGame::getSlug, DetectedGame::getScreenshotIds)));

        int downloadCount = downloadImagesIntoCache(gamesToImageIds, IgdbApiProperties.SCREENSHOT_IMAGE_SIZE, "screenshot", "game");

        stopWatch.stop();

        log.info("Downloaded {} screenshots in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    public void downloadCompanyLogosFromIgdb() {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting company logo download...");
        stopWatch.start();

        Map<String, List<String>> companyToLogoIdMap = detectedGameRepository.findAll().stream()
                .flatMap(g -> g.getCompanies().stream())
                .collect(Collectors.toMap(Company::getSlug, c -> Collections.singletonList(c.getLogoId()), (c1, c2) -> c1));

        MultiValueMap<String, String> companiesToLogoIds = new LinkedMultiValueMap<>(companyToLogoIdMap);

        int downloadCount = downloadImagesIntoCache(companiesToLogoIds, IgdbApiProperties.LOGO_IMAGE_SIZE, "logo", "company");

        stopWatch.stop();

        log.info("Downloaded {} company logos in {} seconds.", downloadCount, (int) stopWatch.getTotalTimeSeconds());
    }

    private void downloadFile(Path path, OutputStream outputStream) {
        try {
            Files.copy(path, outputStream);
        } catch (IOException e) {
            log.error("Error while downloading file:", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load file '%s'.".formatted(path));
        }
    }

    private void downloadFilesAsZip(Path path, OutputStream outputStream) {
        ZipOutputStream zos = new ZipOutputStream(outputStream) {{
            def.setLevel(Deflater.NO_COMPRESSION);
        }};

        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @SneakyThrows
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    zos.putNextEntry(new ZipEntry(path.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });

            zos.close();
        } catch (ClientAbortException e) {
            // Aborted downloads will be handled gracefully
            throw new DownloadAbortedException();
        } catch (IOException e) {
            log.error("Error while zipping files:", e);
        }
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

                    if (!StringUtils.hasText(imageId)) return;

                    String imgFileName = "%s.png".formatted(imageId);
                    String imgUrl = "t_%s/%s".formatted(imageSize, imgFileName);

                    if (Files.exists(Path.of(cacheFolderPath, imgFileName))) {

                        Path existingImageFile = Path.of(cacheFolderPath, imgFileName);

                        try {
                            if(Files.size(existingImageFile) == 0L) {
                                log.info("File '{}' is corrupt, retrying download...", imgFileName);
                                Files.delete(existingImageFile);
                            } else {
                                log.debug("{} for {} '{}' already downloaded ({}), skipping.",
                                        imageType.substring(0, 1).toUpperCase() + imageType.substring(1).toLowerCase(),
                                        entityType,
                                        entry.getKey(),
                                        imgFileName);
                                return;
                            }
                        } catch (IOException e) {
                            log.error("Error while checking file '{}'.", existingImageFile);
                        }
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
