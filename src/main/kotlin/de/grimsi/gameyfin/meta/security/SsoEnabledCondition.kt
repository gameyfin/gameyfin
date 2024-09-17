package de.grimsi.gameyfin.meta.security

import de.grimsi.gameyfin.config.ConfigProperties
import org.springframework.context.annotation.Condition
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.env.Environment
import org.springframework.core.type.AnnotatedTypeMetadata
import java.sql.DriverManager

/**
 * Since we are loading this config so early the Spring context has not fully loaded yet, not even a DataSource.
 * Thankfully the environment is already available, and we can use it to connect to the database.
 * So we are rawdogging the database connection and query execution here.
 */
class SsoEnabledCondition : Condition {
    override fun matches(context: ConditionContext, metadata: AnnotatedTypeMetadata): Boolean {
        try {
            val environment = context.beanFactory!!.getBean(Environment::class.java);
            val url = environment.getProperty("spring.datasource.url");
            val user = environment.getProperty("spring.datasource.username");
            val password = environment.getProperty("spring.datasource.password");
            val connection = DriverManager.getConnection(url, user, password);

            connection.use { connection ->
                val statement = connection.prepareStatement("SELECT \"value\" FROM app_config WHERE \"key\" = ?")
                statement.setString(1, ConfigProperties.SsoEnabled.key)
                val resultSet = statement.executeQuery()
                if (resultSet.next()) {
                    return resultSet.getBoolean("value")
                }
            }
        } catch (_: Exception) {
            return false
        }

        return false
    }
}