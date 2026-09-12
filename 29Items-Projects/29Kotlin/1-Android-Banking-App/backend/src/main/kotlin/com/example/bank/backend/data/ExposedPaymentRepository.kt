package com.example.bank.backend.data

import com.example.bank.backend.model.Account
import com.example.bank.backend.model.FraudAlert
import com.example.bank.backend.model.FraudAssessment
import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.model.Transaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.Clock
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tables mirror db/migration/V1__core_schema.sql (Flyway owns the DDL — Exposed only reads/writes).
 */
object AccountsTable : Table("accounts") {
    val id = varchar("id", 36)
    val userId = varchar("user_id", 36) // Phase 2 auth scoping; single demo user until then
    val name = varchar("name", 100)
    val iban = varchar("iban", 34)
    val balanceMinor = long("balance_minor")
    val currency = char("currency", 3)
    val type = varchar("type", 20)
    override val primaryKey = PrimaryKey(id)
}

object TransactionsTable : Table("transactions") {
    val id = varchar("id", 36)
    val accountId = varchar("account_id", 36)
    val payeeName = varchar("payee_name", 100)
    val payeeIban = varchar("payee_iban", 34)
    val amountMinor = long("amount_minor")
    val currency = char("currency", 3)
    val direction = varchar("direction", 6)
    val status = varchar("status", 10)
    val reference = varchar("reference", 140)
    val fraudScore = decimal("fraud_score", 4, 3)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object IdempotencyKeysTable : Table("idempotency_keys") {
    val key = varchar("key", 64)
    val accountId = varchar("account_id", 36)
    val transactionId = varchar("transaction_id", 36)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(key)
}

object FraudAlertsTable : Table("fraud_alerts") {
    val id = long("id").autoIncrement() // BIGSERIAL
    val referenceKey = varchar("reference_key", 64)
    val accountId = varchar("account_id", 36)
    val score = decimal("score", 4, 3)
    val risk = varchar("risk", 6)
    val reasons = text("reasons")
    val reviewStatus = varchar("review_status", 10)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * PostgreSQL-backed [PaymentRepository] — the Phase 2 target of ARCHITECTURE.md 2.4.
 * Flyway applies V1 before this class is constructed; seed data lands only when the
 * accounts table is empty (fresh volume). Blocking JDBC throughout — the caller
 * ([com.example.bank.backend.service.BankService]) wraps calls in Dispatchers.IO.
 */
class ExposedPaymentRepository(
    private val database: Database,
    private val clock: Clock = Clock.systemDefaultZone(),
) : PaymentRepository {

    fun seedIfEmpty() {
        transaction(database) {
            if (AccountsTable.selectAll().count() > 0) return@transaction
            insertAccount("acc-1", "Main account", "UA903052299999001111223344551", 1_245_075, "CHECKING")
            insertAccount("acc-2", "Savings", "UA903052299999002222334455662", 45_000_000, "SAVINGS")

            // Same demo history as InMemoryPaymentRepository — outside the 1h velocity window.
            insertTransaction("tx-seed-1", "acc-1", "Employer GmbH", "DE89370400440532013000", 850_000, "CREDIT", 3 * DAY_MS)
            insertTransaction("tx-seed-2", "acc-1", "Landlord Rent", "UA213223130000026007233566001", 95_000, "DEBIT", 2 * DAY_MS)
            insertTransaction("tx-seed-3", "acc-1", "Grocery Mart", "UA213223130000026007233566002", 4_320, "DEBIT", 26 * HOUR_MS)
            insertTransaction("tx-seed-4", "acc-2", "Savings Interest", "", 12_505, "CREDIT", 5 * DAY_MS)
        }
    }

    override fun listAccounts(): List<Account> =
        transaction(database) {
            AccountsTable.selectAll().orderBy(AccountsTable.name to SortOrder.ASC).map(::rowToAccount)
        }

    override fun findAccount(id: String): Account? =
        transaction(database) {
            AccountsTable.selectAll().where { AccountsTable.id eq id }.singleOrNull()?.let(::rowToAccount)
        }

    override fun listPayments(accountId: String): List<Transaction> =
        recentTransactions(accountId, limit = Int.MAX_VALUE)

    override fun recentTransactions(accountId: String, limit: Int): List<Transaction> =
        transaction(database) {
            TransactionsTable.selectAll()
                .where { TransactionsTable.accountId eq accountId }
                .orderBy(TransactionsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .map(::rowToTransaction)
        }

    override fun findByIdempotencyKey(key: String): Transaction? =
        transaction(database) {
            // Explicit join condition: Exposed can't infer one because Flyway owns the DDL
            // (no Exposed-level foreign keys are declared).
            IdempotencyKeysTable.join(
                TransactionsTable,
                JoinType.INNER,
                IdempotencyKeysTable.transactionId,
                TransactionsTable.id,
            )
                .selectAll()
                .where { IdempotencyKeysTable.key eq key }
                .singleOrNull()
                ?.let(::rowToTransaction)
        }

    override fun saveFraudAlert(referenceKey: String, accountId: String, assessment: FraudAssessment) {
        transaction(database) {
            FraudAlertsTable.insert {
                it[this.referenceKey] = referenceKey
                it[this.accountId] = accountId
                it[score] = BigDecimal.valueOf(assessment.score)
                it[risk] = assessment.risk.name
                it[reasons] = assessment.reasons.joinToString("; ")
                it[reviewStatus] = "PENDING"
                it[createdAt] = now()
            }
        }
    }

    override fun listFraudAlerts(): List<FraudAlert> =
        transaction(database) {
            FraudAlertsTable.selectAll()
                .orderBy(FraudAlertsTable.createdAt to SortOrder.DESC)
                .map { row ->
                    FraudAlert(
                        id = row[FraudAlertsTable.id],
                        referenceKey = row[FraudAlertsTable.referenceKey],
                        accountId = row[FraudAlertsTable.accountId],
                        score = row[FraudAlertsTable.score].toDouble(),
                        risk = row[FraudAlertsTable.risk],
                        reasons = row[FraudAlertsTable.reasons],
                        reviewStatus = row[FraudAlertsTable.reviewStatus],
                        createdAt = row[FraudAlertsTable.createdAt].toInstant().toEpochMilli(),
                    )
                }
        }

    override fun savePayment(idempotencyKey: String, request: PaymentRequest, fraudScore: Double): TxOutcome =
        transaction(database) {
            // Race-safe on the same key: the unique PK on idempotency_keys fails duplicate inserts;
            // BankService already replays existing keys before calling here.
            val accountRow = AccountsTable.selectAll()
                .where { AccountsTable.id eq request.fromAccountId }
                .forUpdate() // row lock: concurrent debits serialize on the balance
                .singleOrNull()
                ?: return@transaction TxOutcome.InsufficientFunds // caller resolves UnknownAccount first

            val balance = accountRow[AccountsTable.balanceMinor]
            if (balance < request.amountMinor) return@transaction TxOutcome.InsufficientFunds

            val transactionId = UUID.randomUUID().toString() // VARCHAR(36): no room for a prefix
            TransactionsTable.insert {
                it[id] = transactionId
                it[accountId] = request.fromAccountId
                it[payeeName] = request.payeeName
                it[payeeIban] = request.payeeIban
                it[amountMinor] = request.amountMinor
                it[currency] = request.currency
                it[direction] = "DEBIT"
                it[status] = "COMPLETED"
                it[reference] = request.reference
                it[TransactionsTable.fraudScore] = BigDecimal.valueOf(fraudScore)
                it[createdAt] = now()
            }
            AccountsTable.update({ AccountsTable.id eq request.fromAccountId }) {
                it[balanceMinor] = balance - request.amountMinor
            }
            IdempotencyKeysTable.insert {
                it[key] = idempotencyKey
                it[accountId] = request.fromAccountId
                it[IdempotencyKeysTable.transactionId] = transactionId
                it[createdAt] = now()
            }
            TxOutcome.Saved(
                Transaction(
                    id = transactionId,
                    accountId = request.fromAccountId,
                    payeeName = request.payeeName,
                    amountMinor = request.amountMinor,
                    currency = request.currency,
                    direction = "DEBIT",
                    status = "COMPLETED",
                    reference = request.reference,
                    fraudScore = fraudScore,
                    createdAt = clock.millis(),
                )
            )
        }

    // ---- helpers ----

    private fun now(): OffsetDateTime = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)

    private fun insertAccount(id: String, name: String, iban: String, balanceMinor: Long, type: String) {
        AccountsTable.insert {
            it[AccountsTable.id] = id
            it[userId] = DEMO_USER_ID
            it[AccountsTable.name] = name
            it[AccountsTable.iban] = iban
            it[AccountsTable.balanceMinor] = balanceMinor
            it[currency] = "EUR"
            it[AccountsTable.type] = type
        }
    }

    private fun insertTransaction(
        id: String,
        accountId: String,
        payeeName: String,
        payeeIban: String,
        amountMinor: Long,
        direction: String,
        ageMs: Long,
    ) {
        TransactionsTable.insert {
            it[TransactionsTable.id] = id
            it[TransactionsTable.accountId] = accountId
            it[TransactionsTable.payeeName] = payeeName
            it[TransactionsTable.payeeIban] = payeeIban
            it[TransactionsTable.amountMinor] = amountMinor
            it[currency] = "EUR"
            it[TransactionsTable.direction] = direction
            it[status] = "COMPLETED"
            it[reference] = "Seed demo entry"
            it[fraudScore] = BigDecimal.ZERO
            it[createdAt] = OffsetDateTime.ofInstant(clock.instant().minusMillis(ageMs), ZoneOffset.UTC)
        }
    }

    private fun rowToAccount(row: ResultRow) = Account(
        id = row[AccountsTable.id],
        name = row[AccountsTable.name],
        iban = row[AccountsTable.iban],
        balanceMinor = row[AccountsTable.balanceMinor],
        currency = row[AccountsTable.currency],
        type = row[AccountsTable.type],
    )

    private fun rowToTransaction(row: ResultRow) = Transaction(
        id = row[TransactionsTable.id],
        accountId = row[TransactionsTable.accountId],
        payeeName = row[TransactionsTable.payeeName],
        amountMinor = row[TransactionsTable.amountMinor],
        currency = row[TransactionsTable.currency],
        direction = row[TransactionsTable.direction],
        status = row[TransactionsTable.status],
        reference = row[TransactionsTable.reference],
        fraudScore = row[TransactionsTable.fraudScore].toDouble(),
        createdAt = row[TransactionsTable.createdAt].toInstant().toEpochMilli(),
    )

    private companion object {
        const val DEMO_USER_ID = "demo-user"
        const val DAY_MS = 24 * 3_600_000L
        const val HOUR_MS = 3_600_000L
    }
}
