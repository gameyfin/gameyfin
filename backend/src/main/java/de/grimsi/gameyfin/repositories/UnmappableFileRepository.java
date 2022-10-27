package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.UnmappableFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface UnmappableFileRepository extends JpaRepository<UnmappableFile, Long> {

    boolean existsByPath(String path);

    List<UnmappableFile> getAllByPathNotIn(Collection<String> paths);

    List<UnmappableFile> getAllByPathNotInAndPathStartsWith(Collection<String> paths, String libraryPath);


    Optional<UnmappableFile> findByPath(String path);

    default List<UnmappableFile> getAllByPathNotInAndPathStartsWith(List<Path> paths, String libraryPath) {
        List<String> pathStrings = paths.stream().map(Path::toString).toList();
        // get unmapped files that are not in the paths list but are starting with libraryPath if libraryPath is not empty
        return isBlank(libraryPath) ? getAllByPathNotIn(pathStrings) : getAllByPathNotInAndPathStartsWith(pathStrings, libraryPath);
    }
}
