package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.CompanyType
import org.gameyfin.app.games.repositories.CompanyRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CompanyServiceTest {

    private lateinit var companyRepository: CompanyRepository
    private lateinit var companyService: CompanyService

    @BeforeEach
    fun setup() {
        companyRepository = mockk()
        companyService = CompanyService(companyRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `createOrGet should return existing company if found`() {
        val existingCompany = Company(id = 1L, name = "TestCompany", type = CompanyType.DEVELOPER)
        every { companyRepository.findByNameAndType("TestCompany", CompanyType.DEVELOPER) } returns existingCompany

        val result = companyService.createOrGet(Company(name = "TestCompany", type = CompanyType.DEVELOPER))

        assertEquals(existingCompany, result)
        verify(exactly = 1) { companyRepository.findByNameAndType("TestCompany", CompanyType.DEVELOPER) }
        verify(exactly = 0) { companyRepository.save(any()) }
    }

    @Test
    fun `createOrGet should create new company if not found`() {
        val newCompany = Company(name = "NewCompany", type = CompanyType.PUBLISHER)
        val savedCompany = Company(id = 2L, name = "NewCompany", type = CompanyType.PUBLISHER)

        every { companyRepository.findByNameAndType("NewCompany", CompanyType.PUBLISHER) } returns null
        every { companyRepository.save(any()) } returns savedCompany

        val result = companyService.createOrGet(newCompany)

        assertEquals(savedCompany, result)
        verify(exactly = 1) { companyRepository.findByNameAndType("NewCompany", CompanyType.PUBLISHER) }
        verify(exactly = 1) { companyRepository.save(match { it.name == "NewCompany" && it.type == CompanyType.PUBLISHER }) }
    }

    @Test
    fun `createOrGet should handle concurrent insert by fetching again`() {
        val company = Company(name = "ConcurrentCompany", type = CompanyType.DEVELOPER)
        val existingCompany = Company(id = 3L, name = "ConcurrentCompany", type = CompanyType.DEVELOPER)

        every { companyRepository.findByNameAndType("ConcurrentCompany", CompanyType.DEVELOPER) } returnsMany listOf(
            null,
            existingCompany
        )
        every { companyRepository.save(any()) } throws DataIntegrityViolationException("Duplicate key")

        val result = companyService.createOrGet(company)

        assertEquals(existingCompany, result)
        verify(exactly = 2) { companyRepository.findByNameAndType("ConcurrentCompany", CompanyType.DEVELOPER) }
        verify(exactly = 1) { companyRepository.save(any()) }
    }

    @Test
    fun `createOrGet should throw exception if concurrent insert fails and company still not found`() {
        val company = Company(name = "FailedCompany", type = CompanyType.PUBLISHER)
        val exception = DataIntegrityViolationException("Database error")

        every { companyRepository.findByNameAndType("FailedCompany", CompanyType.PUBLISHER) } returns null
        every { companyRepository.save(any()) } throws exception

        try {
            companyService.createOrGet(company)
            throw AssertionError("Expected DataIntegrityViolationException to be thrown")
        } catch (e: DataIntegrityViolationException) {
            assertEquals(exception, e)
        }

        verify(exactly = 2) { companyRepository.findByNameAndType("FailedCompany", CompanyType.PUBLISHER) }
        verify(exactly = 1) { companyRepository.save(any()) }
    }

    @Test
    fun `createOrGet should handle company with same name but different type`() {
        val developer = Company(name = "SameCompany", type = CompanyType.DEVELOPER)
        val publisher = Company(name = "SameCompany", type = CompanyType.PUBLISHER)
        val savedDeveloper = Company(id = 4L, name = "SameCompany", type = CompanyType.DEVELOPER)
        val savedPublisher = Company(id = 5L, name = "SameCompany", type = CompanyType.PUBLISHER)

        every { companyRepository.findByNameAndType("SameCompany", CompanyType.DEVELOPER) } returns null
        every { companyRepository.findByNameAndType("SameCompany", CompanyType.PUBLISHER) } returns null
        every { companyRepository.save(match { it.type == CompanyType.DEVELOPER }) } returns savedDeveloper
        every { companyRepository.save(match { it.type == CompanyType.PUBLISHER }) } returns savedPublisher

        val resultDeveloper = companyService.createOrGet(developer)
        val resultPublisher = companyService.createOrGet(publisher)

        assertEquals(savedDeveloper, resultDeveloper)
        assertEquals(savedPublisher, resultPublisher)
        verify(exactly = 1) { companyRepository.findByNameAndType("SameCompany", CompanyType.DEVELOPER) }
        verify(exactly = 1) { companyRepository.findByNameAndType("SameCompany", CompanyType.PUBLISHER) }
    }

    @Test
    fun `createOrGet should preserve company name and type exactly`() {
        val companyName = "TestCompany With Spaces & Special!@#"
        val company = Company(name = companyName, type = CompanyType.DEVELOPER)
        val savedCompany = Company(id = 6L, name = companyName, type = CompanyType.DEVELOPER)

        every { companyRepository.findByNameAndType(companyName, CompanyType.DEVELOPER) } returns null
        every { companyRepository.save(any()) } returns savedCompany

        val result = companyService.createOrGet(company)

        assertNotNull(result)
        assertEquals(companyName, result.name)
        verify(exactly = 1) {
            companyRepository.save(match { it.name == companyName && it.type == CompanyType.DEVELOPER })
        }
    }

    @Test
    fun `createOrGet should handle empty company name`() {
        val company = Company(name = "", type = CompanyType.DEVELOPER)
        val savedCompany = Company(id = 7L, name = "", type = CompanyType.DEVELOPER)

        every { companyRepository.findByNameAndType("", CompanyType.DEVELOPER) } returns null
        every { companyRepository.save(any()) } returns savedCompany

        val result = companyService.createOrGet(company)

        assertNotNull(result)
        assertEquals("", result.name)
    }

    @Test
    fun `createOrGet should not copy id from input company`() {
        val company = Company(id = 999L, name = "TestCompany", type = CompanyType.DEVELOPER)
        val savedCompany = Company(id = 8L, name = "TestCompany", type = CompanyType.DEVELOPER)

        every { companyRepository.findByNameAndType("TestCompany", CompanyType.DEVELOPER) } returns null
        every { companyRepository.save(any()) } returns savedCompany

        val result = companyService.createOrGet(company)

        assertEquals(8L, result.id)
        verify(exactly = 1) {
            companyRepository.save(match { it.id == null && it.name == "TestCompany" })
        }
    }
}

