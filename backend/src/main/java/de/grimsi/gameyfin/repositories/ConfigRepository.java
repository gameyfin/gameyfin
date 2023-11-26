package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.ConfigProperty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfigRepository extends JpaRepository<ConfigProperty, String> {
}
