package org.gameyfin.app.games

import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.repositories.CompanyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CompanyService(
    private val companyRepository: CompanyRepository
) {
    @Transactional
    fun createOrGet(company: Company): Company {
        companyRepository.findByNameAndType(company.name, company.type)?.let { return it }

        return try {
            val toSave = Company(name = company.name, type = company.type)
            companyRepository.save(toSave)
        } catch (e: DataIntegrityViolationException) {
            // Another transaction may have inserted the same unique (name,type) concurrently; fetch and return
            companyRepository.findByNameAndType(company.name, company.type)
                ?: throw e
        }
    }
}