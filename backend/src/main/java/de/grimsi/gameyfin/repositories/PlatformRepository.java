package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Platform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformRepository extends JpaRepository<Platform, String> {

    boolean existsBySlug(String slug);

    Optional<Platform> findBySlug(String slug);
}
