package org.taskhub.server.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.taskhub.server.models.Households
import org.taskhub.server.models.Members

object DatabaseFactory {
    fun init(environment: ApplicationEnvironment) {
        val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
            ?: "jdbc:h2:mem:taskhub;DB_CLOSE_DELAY=-1"
        val dbUser = environment.config.propertyOrNull("database.user")?.getString() ?: "sa"
        val dbPassword = environment.config.propertyOrNull("database.password")?.getString() ?: ""

        val isH2 = dbUrl.startsWith("jdbc:h2")

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            maximumPoolSize = if (isH2) 5 else 20
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }

        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        if (isH2) {
            // For H2 (dev/testing): use Exposed SchemaUtils to manage tables
            transaction {
                SchemaUtils.create(Households, Members)
            }
        } else {
            // For PostgreSQL (production): use Flyway migrations
            Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate()
        }
    }
}