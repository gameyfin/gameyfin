package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Library;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LibraryRepository extends JpaRepository<Library, String> {

    boolean existsByPathIgnoreCase(String path);

    Optional<Library> findByPath(String path);
}
