package org.gameyfin.app.games

import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.repositories.CompanyRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

@Service
class CompanyService(
    private val companyRepository: CompanyRepository,
    transactionManager: PlatformTransactionManager
) {

    private val requiresNewTx = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    /**
     * Returns an existing company matching (name, type), or creates a new one.
     *
     * Intentionally NOT @Transactional at this level so that a failed save in
     * [trySave] only aborts that inner transaction, leaving this call-site free
     * to retry the lookup without a poisoned transaction context.
     *
     * This pattern avoids both:
     *  - The "detached entity" error caused by REQUIRES_NEW returning an entity
     *    managed by a different persistence context than the outer game-save transaction.
     *  - The "current transaction is aborted" error caused by catching
     *    DataIntegrityViolationException inside a single @Transactional method.
     */
    fun createOrGet(company: Company): Company {
        companyRepository.findByNameAndType(company.name, company.type)?.let { return it }

        return try {
            trySave(Company(name = company.name, type = company.type))
        } catch (e: DataIntegrityViolationException) {
            // Another thread inserted the same (name, type) concurrently — fetch and return it
            companyRepository.findByNameAndType(company.name, company.type)
                ?: throw e
        }
    }

    /**
     * Saves the company in its own isolated transaction.
     * If a concurrent insert already created the same (name, type), this throws
     * DataIntegrityViolationException and only this inner transaction is rolled back,
     * leaving the caller's context clean to perform the fallback lookup.
     */
    fun trySave(company: Company): Company =
        requiresNewTx.execute { companyRepository.saveAndFlush(company) }
}
