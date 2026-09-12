package com.gym.plugins

import com.gym.domain.MembershipTier
import com.gym.domain.Role
import com.gym.repository.*
import com.gym.security.PasswordHasher
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private var dataSource: HikariDataSource? = null

fun Application.configureDatabase() {
    val logger = LoggerFactory.getLogger("DatabaseConfig")

    if (dataSource == null) {
        val url = environment.config.propertyOrNull("database.url")?.getString()
            ?: "jdbc:h2:mem:gymdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        val driver = environment.config.propertyOrNull("database.driver")?.getString()
            ?: if (url.contains("postgresql")) "org.postgresql.Driver" else "org.h2.Driver"
        val user = environment.config.propertyOrNull("database.user")?.getString() ?: "sa"
        val password = environment.config.propertyOrNull("database.password")?.getString() ?: ""

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }

        val ds = HikariDataSource(hikariConfig)
        dataSource = ds
        Database.connect(ds)
        logger.info("Connected to database at: $url")
    } else {
        Database.connect(dataSource!!)
    }

    transaction {
        SchemaUtils.createMissingTablesAndColumns(
            MembersTable,
            PlansTable,
            SubscriptionsTable,
            FreezeRecordsTable,
            CheckInsTable
        )

        // Seed baseline membership plans
        if (PlansTable.selectAll().where { PlansTable.id eq "plan-basic-monthly" }.count() == 0L) {
            val now = Clock.System.now()
            PlansTable.insert {
                it[id] = "plan-basic-monthly"
                it[name] = "Basic Monthly"
                it[tier] = MembershipTier.BASIC
                it[priceCents] = 2999
                it[durationDays] = 30
                it[maxGuestPasses] = 0
                it[features] = "GYM_FLOOR,LOCKER_ROOM"
                it[isActive] = true
                it[createdAt] = now
            }
            PlansTable.insert {
                it[id] = "plan-premium-monthly"
                it[name] = "Premium Monthly"
                it[tier] = MembershipTier.PREMIUM
                it[priceCents] = 5999
                it[durationDays] = 30
                it[maxGuestPasses] = 2
                it[features] = "GYM_FLOOR,LOCKER_ROOM,POOL,SAUNA,GROUP_CLASSES"
                it[isActive] = true
                it[createdAt] = now
            }
            PlansTable.insert {
                it[id] = "plan-vip-annual"
                it[name] = "VIP Annual All-Access"
                it[tier] = MembershipTier.VIP
                it[priceCents] = 59999
                it[durationDays] = 365
                it[maxGuestPasses] = 5
                it[features] = "GYM_FLOOR,LOCKER_ROOM,POOL,SAUNA,GROUP_CLASSES,VIP_LOUNGE,PERSONAL_TRAINER_CONSULT,FREE_TOWEL_SERVICE"
                it[isActive] = true
                it[createdAt] = now
            }
            logger.info("Default gym plans seeded.")
        }

        // Seed initial admin account
        if (MembersTable.selectAll().where { MembersTable.email eq "admin@gym.com" }.count() == 0L) {
            val now = Clock.System.now()
            MembersTable.insert {
                it[id] = "admin-user-001"
                it[email] = "admin@gym.com"
                it[passwordHash] = PasswordHasher.hash("AdminSecret123!")
                it[fullName] = "System Administrator"
                it[badgeCode] = "BADGE-ADMIN-001"
                it[role] = Role.ROLE_ADMIN
                it[createdAt] = now
                it[updatedAt] = now
            }
            logger.info("Default admin account created (admin@gym.com).")
        }
    }
}
