package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.DetectedGame;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.transaction.Transactional;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

public interface DetectedGameRepository extends JpaRepository<DetectedGame, String> {

    boolean existsByPath(String path);

    boolean existsBySlug(String slug);

    List<DetectedGame> getAllByPathNotIn(Collection<String> paths);

    default List<DetectedGame> getAllByPathNotIn(List<Path> paths) {
        List<String> pathStrings = paths.stream().map(Path::toString).toList();
        return getAllByPathNotIn(pathStrings);
    }
}
