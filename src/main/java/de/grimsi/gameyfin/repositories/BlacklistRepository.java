package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.BlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlacklistRepository extends JpaRepository<BlacklistEntry, String> {

    boolean existsByPath(String path);
}
