package com.example.bank.backend.service

import com.example.bank.backend.data.PaymentRepository
import com.example.bank.backend.data.TxOutcome
import com.example.bank.backend.model.Account
import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.model.PaymentResult
import com.example.bank.backend.model.Risk
import com.example.bank.backend.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Business logic tier: routes validate transport concerns, this class owns the money rules.
 * All repository access is wrapped in [Dispatchers.IO] — the PostgreSQL implementation is
 * blocking JDBC and must never run on a Netty event-loop thread.
 */
class BankService(
    private val repository: PaymentRepository,
    private val fraudService: FraudDetectionService,
) {

    suspend fun listAccounts(): List<Account> = withContext(Dispatchers.IO) { repository.listAccounts() }

    suspend fun listPayments(accountId: String): List<Transaction> =
        withContext(Dispatchers.IO) { repository.listPayments(accountId) }

    /** Newest-first transaction history for the statements screen; null when the account is unknown. */
    suspend fun listTransactions(accountId: String, limit: Int): List<Transaction>? =
        withContext(Dispatchers.IO) {
            repository.findAccount(accountId)?.let { repository.recentTransactions(accountId, limit) }
        }

    suspend fun listFraudAlerts() = withContext(Dispatchers.IO) { repository.listFraudAlerts() }

    suspend fun submitPayment(idempotencyKey: String, request: PaymentRequest): PaymentResult =
        withContext(Dispatchers.IO) {
            // 1. Replay protection — a retried request must never charge twice.
            repository.findByIdempotencyKey(idempotencyKey)?.let { return@withContext PaymentResult.Duplicate(it) }

            // 2. Account resolution.
            repository.findAccount(request.fromAccountId)
                ?: return@withContext PaymentResult.Rejected.UnknownAccount(request.fromAccountId)

            // 3. Authoritative fraud scoring (client-side rules are UX only).
            val recent = repository.recentTransactions(request.fromAccountId, limit = 25)
            val assessment = fraudService.assess(request, recent)
            if (assessment.risk == Risk.HIGH) {
                repository.saveFraudAlert(
                    referenceKey = idempotencyKey,
                    accountId = request.fromAccountId,
                    assessment = assessment,
                )
                return@withContext PaymentResult.Rejected.FraudBlocked(assessment.reasons.joinToString("; "))
            }

            // 4. Persist atomically (funds check + debit + idempotency store).
            when (val outcome = repository.savePayment(idempotencyKey, request, assessment.score)) {
                is TxOutcome.Saved -> PaymentResult.Created(outcome.transaction)
                TxOutcome.InsufficientFunds -> PaymentResult.Rejected.InsufficientFunds
            }
        }
}
