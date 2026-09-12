package com.example.bank.backend.model

import kotlinx.serialization.Serializable

@Serializable
data class Account(
    val id: String,
    val name: String,
    val iban: String,
    val balanceMinor: Long,
    val currency: String,
    val type: String,
)

@Serializable
data class PaymentRequest(
    val fromAccountId: String,
    val payeeName: String,
    val payeeIban: String,
    /** Minor units (cents) — never a floating-point amount. */
    val amountMinor: Long,
    val currency: String,
    val reference: String = "",
)

@Serializable
data class Transaction(
    val id: String,
    val accountId: String,
    val payeeName: String,
    val amountMinor: Long,
    val currency: String,
    val direction: String, // DEBIT | CREDIT
    val status: String,    // PENDING | COMPLETED | DECLINED
    val reference: String,
    val fraudScore: Double,
    val createdAt: Long,
)

@Serializable
data class ErrorEnvelope(val message: String)

enum class Risk { LOW, MEDIUM, HIGH }

data class FraudAssessment(
    val score: Double,
    val risk: Risk,
    val reasons: List<String> = emptyList(),
)

/**
 * Review-queue entry persisted whenever the server engine returns HIGH
 * (V1 schema table `fraud_alerts` — see db/migration/V1__core_schema.sql).
 */
data class FraudAlert(
    val id: Long,
    val referenceKey: String,
    val accountId: String,
    val score: Double,
    val risk: String,
    val reasons: String,
    val reviewStatus: String,
    val createdAt: Long,
)

sealed class PaymentResult {
    data class Created(val payment: Transaction) : PaymentResult()
    data class Duplicate(val payment: Transaction) : PaymentResult()

    sealed class Rejected : PaymentResult() {
        data object InsufficientFunds : Rejected()
        data class FraudBlocked(val reason: String) : Rejected()
        data class UnknownAccount(val accountId: String) : Rejected()
    }
}

private const val MAX_AMOUNT_MINOR = 100_000_000L // 1,000,000.00 major units

/** Field-level validation surfaced to clients as 422 with readable reasons. */
fun PaymentRequest.validate(): List<String> = buildList {
    if (fromAccountId.isBlank()) add("fromAccountId is required")
    if (payeeName.isBlank()) add("payeeName is required")
    if (payeeIban.isBlank()) add("payeeIban is required")
    if (amountMinor <= 0) add("amountMinor must be positive")
    if (amountMinor > MAX_AMOUNT_MINOR) add("amountMinor exceeds the per-transfer maximum")
    if (currency.isBlank()) add("currency is required")
}
