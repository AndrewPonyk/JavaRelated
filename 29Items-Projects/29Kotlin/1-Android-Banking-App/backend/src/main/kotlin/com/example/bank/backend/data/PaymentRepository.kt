package com.example.bank.backend.data

import com.example.bank.backend.model.Account
import com.example.bank.backend.model.FraudAlert
import com.example.bank.backend.model.FraudAssessment
import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.model.Transaction
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed class TxOutcome {
    data class Saved(val transaction: Transaction) : TxOutcome()
    data object InsufficientFunds : TxOutcome()
}

interface PaymentRepository {
    fun listAccounts(): List<Account>
    fun findAccount(id: String): Account?
    fun listPayments(accountId: String): List<Transaction>
    fun recentTransactions(accountId: String, limit: Int): List<Transaction>
    fun findByIdempotencyKey(key: String): Transaction?
    fun saveFraudAlert(referenceKey: String, accountId: String, assessment: FraudAssessment)
    fun listFraudAlerts(): List<FraudAlert>
    fun savePayment(idempotencyKey: String, request: PaymentRequest, fraudScore: Double): TxOutcome
}

/**
 * Phase-1 in-memory implementation with demo seed data; the Flyway schema in
 * src/main/resources/db/migration is the contract this will grow into.
 *
 * TODO(Phase 2): replace with Exposed + HikariCP against PostgreSQL — the atomic
 * lock below becomes a real DB transaction.
 *
 * [clock] is injected so seeded timestamps and the velocity rule stay deterministic
 * under test (TECH-NOTES 3.6 #10).
 */
class InMemoryPaymentRepository(private val clock: Clock = Clock.systemDefaultZone()) : PaymentRepository {

    private val lock = Any()
    private val idGenerator = AtomicLong(1)
    private val fraudAlertId = AtomicLong(1)

    private val accounts = ConcurrentHashMap<String, Account>()
    private val transactionsByAccount = ConcurrentHashMap<String, MutableList<Transaction>>()
    private val transactionById = ConcurrentHashMap<String, Transaction>()
    private val paymentsByIdempotencyKey = ConcurrentHashMap<String, Transaction>()
    private val fraudAlerts = ConcurrentHashMap<Long, FraudAlert>()

    init {
        seedAccount("acc-1", "Main account", "UA903052299999001111223344551", 1_245_075, "CHECKING")
        seedAccount("acc-2", "Savings", "UA903052299999002222334455662", 45_000_000, "SAVINGS")

        // Demo history so the statements screen is populated on a fresh backend.
        // All entries are older than the 1h velocity window so fraud rules stay quiet.
        seedTransaction("tx-seed-1", "acc-1", "Employer GmbH", "DE89370400440532013000", 850_000, "CREDIT", 3 * DAY_MS)
        seedTransaction("tx-seed-2", "acc-1", "Landlord Rent", "UA213223130000026007233566001", 95_000, "DEBIT", 2 * DAY_MS)
        seedTransaction("tx-seed-3", "acc-1", "Grocery Mart", "UA213223130000026007233566002", 4_320, "DEBIT", 26 * HOUR_MS)
        seedTransaction("tx-seed-4", "acc-2", "Savings Interest", "", 12_505, "CREDIT", 5 * DAY_MS)
    }

    private fun seedAccount(id: String, name: String, iban: String, balanceMinor: Long, type: String) {
        accounts[id] = Account(
            id = id, name = name, iban = iban, balanceMinor = balanceMinor,
            currency = "EUR", type = type,
        )
    }

    private fun seedTransaction(
        id: String,
        accountId: String,
        payeeName: String,
        payeeIban: String,
        amountMinor: Long,
        direction: String,
        ageMs: Long,
    ) {
        val transaction = Transaction(
            id = id,
            accountId = accountId,
            payeeName = payeeName,
            amountMinor = amountMinor,
            currency = "EUR",
            direction = direction,
            status = "COMPLETED",
            reference = "Seed demo entry",
            fraudScore = 0.0,
            createdAt = clock.millis() - ageMs,
        )
        transactionsByAccount.computeIfAbsent(accountId) { mutableListOf() }.add(transaction)
        transactionById[id] = transaction
    }

    override fun listAccounts(): List<Account> = accounts.values.sortedBy { it.name }

    override fun findAccount(id: String): Account? = accounts[id]

    override fun listPayments(accountId: String): List<Transaction> =
        recentTransactions(accountId, limit = Int.MAX_VALUE)

    override fun recentTransactions(accountId: String, limit: Int): List<Transaction> =
        transactionsByAccount[accountId].orEmpty()
            .sortedByDescending { it.createdAt }
            .take(limit)

    override fun findByIdempotencyKey(key: String): Transaction? = paymentsByIdempotencyKey[key]

    override fun saveFraudAlert(referenceKey: String, accountId: String, assessment: FraudAssessment) {
        val alert = FraudAlert(
            id = fraudAlertId.getAndIncrement(),
            referenceKey = referenceKey,
            accountId = accountId,
            score = assessment.score,
            risk = assessment.risk.name,
            reasons = assessment.reasons.joinToString("; "),
            reviewStatus = "PENDING",
            createdAt = clock.millis(),
        )
        fraudAlerts[alert.id] = alert
    }

    override fun listFraudAlerts(): List<FraudAlert> =
        fraudAlerts.values.sortedByDescending { it.createdAt }

    override fun savePayment(idempotencyKey: String, request: PaymentRequest, fraudScore: Double): TxOutcome =
        synchronized(lock) {
            val account = accounts[request.fromAccountId]
                ?: return TxOutcome.InsufficientFunds // caller resolves UnknownAccount first
            if (account.balanceMinor < request.amountMinor) return TxOutcome.InsufficientFunds

            val transactionId = "tx-${idGenerator.getAndIncrement()}"
            val transaction = Transaction(
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

            accounts[request.fromAccountId] = account.copy(balanceMinor = account.balanceMinor - request.amountMinor)
            transactionsByAccount.computeIfAbsent(request.fromAccountId) { mutableListOf() }.add(transaction)
            transactionById[transactionId] = transaction
            paymentsByIdempotencyKey[idempotencyKey] = transaction
            TxOutcome.Saved(transaction)
        }

    private companion object {
        const val DAY_MS = 24 * 3_600_000L
        const val HOUR_MS = 3_600_000L
    }
}
