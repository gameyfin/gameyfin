package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.DetectedGame;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetectedGameRepository extends JpaRepository<DetectedGame, String> {

    boolean existsByPath(String path);
    boolean existsBySlug(String slug);
}
