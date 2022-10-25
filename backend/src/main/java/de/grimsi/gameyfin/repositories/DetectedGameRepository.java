package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface DetectedGameRepository extends JpaRepository<DetectedGame, String> {

    boolean existsByPath(String path);

    boolean existsBySlug(String slug);

    Optional<DetectedGame> findByPath(String path);

    List<DetectedGame> findByPathStartsWithAndLibraryIsNull(String path);

    List<DetectedGame> getAllByPathNotIn(Collection<String> paths);
    List<DetectedGame> getAllByPathNotInAndPathStartsWith(Collection<String> paths, String libraryPath);

    default List<DetectedGame> getAllByPathNotInAndPathStartsWith(List<Path> paths, String libraryPath) {
        List<String> pathStrings = paths.stream().map(Path::toString).toList();
        // get games that are not in the paths list but are starting with libraryPath if libraryPath is not empty
        return isBlank(libraryPath) ? getAllByPathNotIn(pathStrings) : getAllByPathNotInAndPathStartsWith(pathStrings, libraryPath);
    }



}
