package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.UnmappableFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface UnmappableFileRepository extends JpaRepository<UnmappableFile, Long> {

    boolean existsByPath(String path);

    List<UnmappableFile> getAllByPathNotIn(Collection<String> paths);

    default List<UnmappableFile> getAllByPathNotIn(List<Path> paths) {
        List<String> pathStrings = paths.stream().map(Path::toString).toList();
        return getAllByPathNotIn(pathStrings);
    }
}
