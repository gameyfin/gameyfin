package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Keyword;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KeywordRepository extends JpaRepository<Keyword, String> {
}
