package com.example.bank.backend.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.Clock

/** Resolved from application.conf (bank.database.*) — see application.conf for env overrides. */
data class DatabaseConfig(
    val enabled: Boolean,
    val url: String,
    val user: String,
    val password: String,
)

object DatabaseFactory {

    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    /**
     * Hikari pool + Flyway migration (classpath:db/migration) + Exposed binding.
     * V1__core_schema.sql owns the DDL; seeding happens afterwards when the tables are empty.
     */
    fun connect(config: DatabaseConfig, clock: Clock): ExposedPaymentRepository {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = 10
                minimumIdle = 2
                poolName = "bank-pg"
            }
        )
        // Docker runs with FLYWAY_LOCATIONS=filesystem:/app/migration (fat-jar classpath
        // scanning proved unreliable — see backend/Dockerfile); local runs use the default.
        val locations = System.getenv("FLYWAY_LOCATIONS")?.split(',')?.toTypedArray()
            ?: arrayOf("classpath:db/migration")
        val result = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations)
            .validateMigrationNaming(true) // fail fast on misnamed migrations instead of silently skipping
            .load()
            .migrate()
        log.info("Flyway applied ${result.migrationsExecuted} migration(s)")

        val repository = ExposedPaymentRepository(Database.connect(dataSource), clock)
        repository.seedIfEmpty()
        log.info("PostgreSQL repository ready (${config.url})")
        return repository
    }
}
