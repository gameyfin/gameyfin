package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.CompanyType
import org.springframework.data.jpa.repository.JpaRepository

interface CompanyRepository : JpaRepository<Company, Long> {
    fun findByNameAndType(name: String, type: CompanyType): Company?
}