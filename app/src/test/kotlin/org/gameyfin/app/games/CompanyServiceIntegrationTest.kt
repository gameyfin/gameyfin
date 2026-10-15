package org.gameyfin.app.games

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import com.zaxxer.hikari.HikariDataSource
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.CompanyType
import org.gameyfin.app.games.repositories.CompanyRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.FilterType
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringJUnitConfig(CompanyServiceIntegrationTest.Config::class)
class CompanyServiceIntegrationTest {
    @Autowired
    private lateinit var companyService: CompanyService

    @Autowired
    private lateinit var companyRepository: CompanyRepository

    @Autowired
    private lateinit var entityManagerFactory: EntityManagerFactory

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var lookupBarrier: CompanyLookupBarrier

    private val entityManager: EntityManager
        get() = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory)

    @BeforeEach
    fun clearDatabase() {
        TransactionTemplate(transactionManager).executeWithoutResult {
            entityManager.createQuery("delete from CompanyScanRecord").executeUpdate()
            companyRepository.deleteAllInBatch()
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 4])
    fun `concurrent game transactions can share a newly discovered publisher`(workers: Int) {
        // Both real H2 lookups must miss before either worker attempts its insert.
        lookupBarrier.barrier = CyclicBarrier(workers)
        try {
            Executors.newFixedThreadPool(workers).use { executor ->
                val results = (1..workers).map { index ->
                    executor.submit<Long> {
                        TransactionTemplate(transactionManager).execute {
                            val publisher = companyService.createOrGet(
                                Company(name = "Bethesda Softworks", type = CompanyType.PUBLISHER)
                            )
                            entityManager.persist(
                                CompanyScanRecord(title = "Game $index", publishers = mutableListOf(publisher))
                            )
                            entityManager.flush()
                            publisher.id!!
                        }
                    }
                }.map { it.get(20, TimeUnit.SECONDS) }

                assertEquals(1, results.toSet().size)
                assertEquals(1, companyRepository.count())
                assertEquals(workers.toLong(), scanRecordCount())
            }
        } finally {
            lookupBarrier.barrier = null
        }
    }

    @Test
    fun `new and existing companies remain usable in the surrounding game transaction`() {
        val companyIds = TransactionTemplate(transactionManager).execute {
            val publisher = companyService.createOrGet(Company(name = "Shared", type = CompanyType.PUBLISHER))
            val samePublisher = companyService.createOrGet(Company(name = "Shared", type = CompanyType.PUBLISHER))
            val developer = companyService.createOrGet(Company(name = "Shared", type = CompanyType.DEVELOPER))
            assertEquals(publisher.id, samePublisher.id)

            entityManager.persist(
                CompanyScanRecord(
                    title = "Game",
                    publishers = mutableListOf(publisher),
                    developers = mutableListOf(developer)
                )
            )
            entityManager.flush()
            listOf(publisher.id, developer.id)
        }

        assertNotNull(companyIds)
        assertEquals(2, companyIds.toSet().size)
        assertEquals(2, companyRepository.count())
        assertEquals(1, scanRecordCount())
    }

    private fun scanRecordCount(): Long = TransactionTemplate(transactionManager).execute {
        entityManager.createQuery("select count(g) from CompanyScanRecord g", Long::class.javaObjectType)
            .singleResult
    }!!

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableAspectJAutoProxy
    @EnableJpaRepositories(
        basePackageClasses = [CompanyRepository::class],
        includeFilters = [ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [CompanyRepository::class])]
    )
    @Import(CompanyService::class, CompanyCreator::class)
    class Config {
        @Bean(destroyMethod = "close")
        fun dataSource() = HikariDataSource().apply {
            jdbcUrl = "jdbc:h2:mem:company-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
            username = "sa"
            password = ""
            maximumPoolSize = 10
            connectionTimeout = 5000
        }

        @Bean
        fun entityManagerFactory(dataSource: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
            setDataSource(dataSource)
            jpaVendorAdapter = HibernateJpaVendorAdapter()
            setManagedTypes(PersistenceManagedTypes.of(Company::class.java.name, CompanyScanRecord::class.java.name))
            setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop", "hibernate.jdbc.batch_size" to 25))
        }

        @Bean
        fun transactionManager(entityManagerFactory: EntityManagerFactory) = JpaTransactionManager(entityManagerFactory)

        @Bean
        fun lookupBarrier() = CompanyLookupBarrier()
    }
}

@Aspect
class CompanyLookupBarrier {
    @Volatile
    var barrier: CyclicBarrier? = null

    @Around("execution(* org.gameyfin.app.games.repositories.CompanyRepository.findByNameAndType(..))")
    fun synchronizeMissingLookups(joinPoint: ProceedingJoinPoint): Any? {
        val result = joinPoint.proceed()
        if (result == null) barrier?.await(10, TimeUnit.SECONDS)
        return result
    }
}

// Keep the same company association cascades as Game, without starting unrelated app services.
@Entity
@Table(name = "company_scan_record")
class CompanyScanRecord(
    @Id @GeneratedValue var id: Long? = null,
    var title: String = "",
    @ManyToMany(cascade = [CascadeType.MERGE, CascadeType.REFRESH])
    @JoinTable(name = "company_scan_publishers")
    var publishers: MutableList<Company> = mutableListOf(),
    @ManyToMany(cascade = [CascadeType.MERGE, CascadeType.REFRESH])
    @JoinTable(name = "company_scan_developers")
    var developers: MutableList<Company> = mutableListOf()
)
