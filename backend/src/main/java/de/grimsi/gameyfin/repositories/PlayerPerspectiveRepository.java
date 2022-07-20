package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.PlayerPerspective;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerPerspectiveRepository extends JpaRepository<PlayerPerspective, String> {
}
