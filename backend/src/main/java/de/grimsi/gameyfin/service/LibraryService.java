package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.dto.LibraryScanResult;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.entities.Platform;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import de.grimsi.gameyfin.repositories.LibraryRepository;
import de.grimsi.gameyfin.repositories.PlatformRepository;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.grimsi.gameyfin.util.FilenameUtil.getFilenameWithoutAdditions;
import static de.grimsi.gameyfin.util.FilenameUtil.hasGameArchiveExtension;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryService {

    @Value("${gameyfin.sources}")
    private List<String> libraryFolders;

    private final IgdbWrapper igdbWrapper;
    private final GameMapper gameMapper;
    private final DetectedGameRepository detectedGameRepository;
    private final UnmappableFileRepository unmappableFileRepository;
    private final LibraryRepository libraryRepository;
    private final PlatformRepository platformRepository;

    public List<Path> getGameFiles() {
        return getGameFiles(null);
    }

    public List<Path> getGameFiles(String path) {
        List<Path> gamefiles = new ArrayList<>();

        libraryFolders.stream().map(Path::of).filter(allPathsOrSpecific(path)).forEach(
                folder -> {
                    try (Stream<Path> stream = Files.list(folder)) {
                        // return all sub-folders (non-recursive) and files that have an extension that indicates that they are a downloadable file
                        List<Path> gameFilesFromThisFolder = stream
                                .filter(p -> Files.isDirectory(p) || hasGameArchiveExtension(p))
                                // filter out all hidden files and folders
                                .filter(p -> {
                                    try {
                                        return !(Files.isHidden(p));
                                    } catch (IOException e) {
                                        throw new RuntimeException("Error while checking if '%s' is hidden.".formatted(p), e);
                                    }
                                })
                                // filter out all empty directories
                                .filter(p -> {
                                    if (!Files.isDirectory(p)) return true;

                                    try (DirectoryStream<Path> s = Files.newDirectoryStream(p)) {
                                        return s.iterator().hasNext();
                                    } catch (IOException e) {
                                        throw new RuntimeException("Error while checking if folder '%s' is empty.".formatted(p), e);
                                    }
                                })
                                .toList();

                        gamefiles.addAll(gameFilesFromThisFolder);

                    } catch (IOException e) {
                        throw new RuntimeException("Error while opening library folder '%s'".formatted(folder), e);
                    }
                }
        );

        return gamefiles;
    }

    private static Predicate<Path> allPathsOrSpecific(String path) {
        return p -> isBlank(path) || p.equals(Path.of(path));
    }

    public LibraryScanResult scanGameLibrary(Library library) {
        StopWatch stopWatch = new StopWatch();

        log.info("Starting scan...");
        stopWatch.start();

        AtomicInteger newUnmappedFilesCounter = new AtomicInteger();

        String libraryPath = library.getPath();
        List<Path> gameFiles = getGameFiles(libraryPath);

        // Check if any games that are in the library have been removed from the file system
        // This would include renamed files, but they will be re-detected by the next step
        List<DetectedGame> deletedGames = detectedGameRepository.getAllByPathNotInAndPathStartsWith(gameFiles, libraryPath);
        detectedGameRepository.deleteAll(deletedGames);
        deletedGames.forEach(g -> log.info("Game '{}' has been moved or deleted.", g.getPath()));

        // Now check if there are any unmapped files that have been removed from the file system
        List<UnmappableFile> deletedUnmappableFiles = unmappableFileRepository.getAllByPathNotInAndPathStartsWith(gameFiles, libraryPath);
        unmappableFileRepository.deleteAll(deletedUnmappableFiles);
        deletedUnmappableFiles.forEach(g -> log.info("Unmapped file '{}' has been moved or deleted.", g.getPath()));

        // Filter out the games we already know and the ones we already tried to map to a game without success
        gameFiles = gameFiles.stream()
                .filter(g -> !detectedGameRepository.existsByPath(g.toString()))
                .filter(g -> !unmappableFileRepository.existsByPath(g.toString()))
                .peek(p -> log.info("Found new potential game: {}", p))
                .toList();

        // Check if library has assigned platforms, so we can search for matching games by specific platforms to get a more accurate match
        Set<String> platformsFilter = libraryRepository.findByPath(libraryPath).map(Library::getPlatforms)
                .map(platforms -> platforms.stream()
                        .map(Platform::getSlug)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());

        // For each new game, load the info from IGDB
        // If a game is not found on IGDB, add it to the list of unmapped files, so we won't query the API later on for the same path
        // If a game is not found on IGDB, blacklist the path, so we won't query the API later for the same path
        List<DetectedGame> newDetectedGames = gameFiles.parallelStream()
                .map(p -> {
                    Optional<Igdb.Game> optionalGame = igdbWrapper.searchForGameByTitle(getFilenameWithoutExtension(p), platformsFilter);

                    if (optionalGame.isPresent() && detectedGameRepository.existsBySlug(optionalGame.get().getSlug())) {
                        log.warn("Game with slug '{}' already exists in database", optionalGame.get().getSlug());
                        optionalGame = Optional.empty();
                    }

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
                .map(e -> gameMapper.toDetectedGame(e.getValue(), e.getKey(), library))
                .collect(toList());

        List<DetectedGame> duplicateGames = getDuplicates(newDetectedGames);
        newUnmappedFilesCounter.getAndAdd(duplicateGames.size());
        newDetectedGames.removeAll(duplicateGames);

        try {
            newDetectedGames = detectedGameRepository.saveAll(newDetectedGames);
        } catch (Exception e) {
            log.error("Could not save {} detected games!", newDetectedGames.size());
            List<UnmappableFile> unmappableFiles = newDetectedGames.stream()
                    .map(game -> new UnmappableFile(game.getPath())).toList();
            unmappableFileRepository.saveAll(unmappableFiles);
        }

        stopWatch.stop();

        log.info("Scan finished in {} seconds: Found {} new games, deleted {} games, could not map {} files/folders, {} games total.",
                (int) stopWatch.getTotalTimeSeconds(), newDetectedGames.size(), deletedGames.size() + deletedUnmappableFiles.size(), newUnmappedFilesCounter.get(), detectedGameRepository.count());

        return LibraryScanResult.builder()
                .newGames(newDetectedGames.size())
                .deletedGames(deletedGames.size() + deletedUnmappableFiles.size())
                .newUnmappableFiles(newUnmappedFilesCounter.get())
                .totalGames((int) detectedGameRepository.count())
                .build();
    }

    public List<AutocompleteSuggestionDto> getAutocompleteSuggestions(String searchTerm, int limit) {
        return igdbWrapper.findPossibleMatchingTitles(searchTerm, limit);
    }

    private List<DetectedGame> getDuplicates(List<DetectedGame> gamesToFilter) {
        return gamesToFilter.stream().filter(g -> Collections.frequency(gamesToFilter, g) > 1)
                .peek(d -> {
                    log.warn("Found duplicate for game '{}' under path '{}'. Mapping must be done manually.", d.getTitle(), d.getPath());
                    unmappableFileRepository.save(new UnmappableFile(d.getPath()));
                })
                .toList();
    }

    public List<Library> getLibraries() {
        return libraryRepository.findAll();
    }

    public Library getLibrary(String path) {
        return libraryRepository.findByPath(path).orElse(null);
    }

    public List<Library> getOrCreateLibraries() {
        libraryFolders.stream().map(Path::of)
                .filter(path -> path.toFile().isDirectory()) // check if path is a valid directory
                .filter(path -> !libraryRepository.existsByPathIgnoreCase(path.toString()))
                .forEach(path -> {
                    // save new paths as library without platforms
                    Library library = new Library(path.toString(), List.of());
                    libraryRepository.save(library);
                });

        List<Library> libraries = libraryRepository.findAll();
        libraries.forEach(library -> {
            // remap existing games to this library as well
            List<DetectedGame> gamesWithoutLibraryAssignment =
                    detectedGameRepository.findByPathStartsWithAndLibraryIsNull(library.getPath());
            gamesWithoutLibraryAssignment.forEach(game -> game.setLibrary(library));
            detectedGameRepository.saveAll(gamesWithoutLibraryAssignment);
        });
        return libraries;
    }

    public List<Platform> getPlatforms(String searchTerm, int limit) {
        return igdbWrapper.findPlatforms(searchTerm, limit);
    }

    public Library mapPlatformsToLibrary(String path, String slugs) {
        Library library = libraryRepository.findByPath(path)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Could not find library for path %s".formatted(path)));

        Set<String> platformSlugs = Arrays.stream(slugs.split(",")).collect(toSet());
        List<Platform> platforms = platformSlugs.stream()
                .map(slug -> platformRepository.findBySlug(slug).
                        orElseGet(() -> igdbWrapper.getPlatformBySlug(slug)))
                .filter(Objects::nonNull)
                .collect(toList());

        library.setPlatforms(platforms);
        libraryRepository.save(library);

        return library;
    }
}
