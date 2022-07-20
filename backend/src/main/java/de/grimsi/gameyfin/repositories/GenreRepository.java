package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, String> {
}
