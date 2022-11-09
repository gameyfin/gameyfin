package de.grimsi.gameyfin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.*;

/**
 * This class handles all filesystem operations for Gameyfin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilesystemService {

    @Value("${gameyfin.cache}")
    private String cacheFolderPath;

    private final FileSystem fileSystem;

    public Path getPath(String path) {
        return fileSystem.getPath(path);
    }

    /**
     * This method will create the folder specified in the "gameyfin.cache" property.
     */
    public void createCacheFolder() {
        log.debug("Creating cache folder...");

        try {
            fileSystem.provider().createDirectory(fileSystem.getPath(cacheFolderPath));
            log.debug("Cache folder created.");

        } catch (FileAlreadyExistsException e) {
            log.debug("Cache folder already existed, no need to create it again.");
        } catch (IOException e) {
            log.error("Error while creating the cache folder.", e);
        }
    }

    public void saveFileToCache(Flux<DataBuffer> dataBuffer, String filename) {
        DataBufferUtils.write(dataBuffer, Path.of(cacheFolderPath).resolve(filename), StandardOpenOption.CREATE)
                .share().block();
    }

    public ByteArrayResource getFileFromCache(String filename) {
        try {
            return new ByteArrayResource(Files.readAllBytes(Paths.get("%s/%s".formatted(cacheFolderPath, filename))));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find image file %s".formatted(filename));
        }
    }

    public void deleteFileFromCache(String filename) {
        try {
            Files.delete(getPathFromFilename(filename));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isCachedFileCorrupt(String filename) {
        try {
            return Files.size(getPathFromFilename(filename)) == 0L;
        } catch (IOException e) {
            log.error("Could not determine file size of '{}'", filename);
            return true;
        }
    }

    public boolean doesCachedFileExist(String filename) {
        return Files.exists(getPathFromFilename(filename));
    }

    public long getSizeOnDisk(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            // Some benchmarks I did have shown that trying to parallelize this process makes it slower instead of faster
            return FileUtils.sizeOfDirectory(path.toFile());
        } else {
            try {
                return Files.size(path);
            } catch (IOException e) {
                log.error("Error while calculating size of file '{}'.", path);
                throw e;
            }
        }
    }

    private Path getPathFromFilename(String filename) {
        return fileSystem.getPath(cacheFolderPath, filename);
    }
}
