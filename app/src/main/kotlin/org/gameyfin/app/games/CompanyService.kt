package org.gameyfin.app.games

import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.repositories.CompanyRepository
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