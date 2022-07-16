package de.grimsi.gameyfin.repositories;

import de.grimsi.gameyfin.entities.Company;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, String> {
}
