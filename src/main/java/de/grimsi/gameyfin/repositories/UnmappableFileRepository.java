package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.UnmappableFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnmappableFileRepository extends JpaRepository<UnmappableFile, String> {

    boolean existsByPath(String path);
}
