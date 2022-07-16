package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.UnmappableFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmappableFileRepository extends JpaRepository<UnmappableFile, Long> {

    boolean existsByPath(String path);
}
