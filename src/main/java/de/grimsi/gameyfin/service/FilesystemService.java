package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
public class FilesystemService {
    @Value("${gameyfin.root}")
    private String rootFolderPath;

    @Value("${gameyfin.file-extensions}")
    private List<String> possibleGameFileExtensions;

    @Autowired
    private IgdbWrapper igdbWrapper;

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private UnmappableFileRepository unmappableFileRepository;

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

    public List<String> getGameFileNames() {
        return this.getGameFiles().stream().map(this::getFilename).toList();
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
                        log.info("Added path '{}' to blacklist", p);
                        return Optional.empty();
                    });
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(e -> GameMapper.toDetectedGame(e.getValue(), e.getKey()))
                .toList();

        newDetectedGames = detectedGameRepository.saveAll(newDetectedGames);

        stopWatch.stop();

        log.info("Scan finished in {} seconds: Found {} new games, deleted {} games, could not map {} files/folders, {} games total.",
                (int) stopWatch.getTotalTimeSeconds(), newDetectedGames.size(), deletedGames.size() + deletedUnmappableFiles.size(), newUnmappedFilesCounter.get(), detectedGameRepository.count());
    }

    private String getFilename(Path p) {
        return FilenameUtils.getBaseName(p.toString());
    }
}
