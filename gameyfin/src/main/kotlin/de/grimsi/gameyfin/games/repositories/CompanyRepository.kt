package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Company
import de.grimsi.gameyfin.games.entities.CompanyType
import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<Company, Long> {
    fun findByNameAndType(name: String, type: CompanyType): Company?
}