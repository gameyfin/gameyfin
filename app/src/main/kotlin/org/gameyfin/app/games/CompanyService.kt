package org.gameyfin.app.games

import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.repositories.CompanyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    private val companyCreator: CompanyCreator
) {
    fun createOrGet(company: Company): Company {
        companyRepository.findByNameAndType(company.name, company.type)?.let { return it }

        return try {
            val toSave = Company(name = company.name, type = company.type)
            companyCreator.create(toSave)
        } catch (e: DataIntegrityViolationException) {
            // The failed insert has rolled back separately, leaving the caller free to read the winner.
            companyRepository.findByNameAndType(company.name, company.type)
                ?: throw e
        }
    }
}

@Service
class CompanyCreator(private val companyRepository: CompanyRepository) {
    // Use a separate bean so Spring applies the transaction boundary before the caller catches a duplicate.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(company: Company): Company = companyRepository.saveAndFlush(company)
}
