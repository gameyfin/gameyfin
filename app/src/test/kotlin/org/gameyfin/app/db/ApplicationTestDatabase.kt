package org.gameyfin.app.db

import jakarta.persistence.EntityManagerFactory
import java.util.*
import javax.sql.DataSource
import liquibase.integration.spring.SpringLiquibase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

/** Both URLs create disposable databases. The PostgreSQL driver owns the test container. */
internal fun applicationTestDatabaseUrl(name: String): String =
    if (System.getenv("GAMEYFIN_TEST_POSTGRES") == "true") "jdbc:tc:postgresql:16-alpine:///$name?TC_DAEMON=true"
    else "jdbc:h2:mem:$name;DB_CLOSE_DELAY=-1"

@TestConfiguration(proxyBeanMethods = false)
class ApplicationJpaTestConfig {
    @Bean
    fun dataSource(): DataSource =
        DriverManagerDataSource(applicationTestDatabaseUrl("app-${UUID.randomUUID()}"), "sa", "").also {
            SpringLiquibase().apply {
                dataSource = it
                changeLog = "classpath:/db/changelog/db.changelog-master.yaml"
                resourceLoader = DefaultResourceLoader()
                afterPropertiesSet()
            }
        }

    @Bean
    fun entityManagerFactory(ds: DataSource) = LocalContainerEntityManagerFactoryBean().apply {
        setDataSource(ds)
        setPackagesToScan("org.gameyfin.app")
        jpaVendorAdapter = HibernateJpaVendorAdapter()
        setJpaPropertyMap(mapOf(
            "hibernate.implicit_naming_strategy" to "org.springframework.boot.hibernate.SpringImplicitNamingStrategy",
            "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"
        ))
    }

    @Bean
    fun transactionManager(factory: EntityManagerFactory) = JpaTransactionManager(factory)
}
