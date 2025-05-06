package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.games.entities.Company
import de.grimsi.gameyfin.games.repositories.CompanyRepository
import org.springframework.stereotype.Service

@Service
class CompanyService(
    private val companyRepository: CompanyRepository
) {
    fun createOrGet(company: Company): Company {
        companyRepository.findByNameAndType(company.name, company.type)?.let { return it }

        val company = Company(name = company.name, type = company.type)
        return companyRepository.save(company)
    }
}