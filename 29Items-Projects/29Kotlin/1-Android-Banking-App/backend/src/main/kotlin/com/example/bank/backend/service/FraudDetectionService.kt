package com.example.bank.backend.service

import com.example.bank.backend.model.FraudAssessment
import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.model.Risk
import com.example.bank.backend.model.Transaction
import java.time.Clock

/**
 * Authoritative fraud scoring before a payment is persisted (ARCHITECTURE.md 2.2).
 *
 * v1 is a deterministic rule set kept in lockstep with the on-device
 * RuleBasedFraudDetectionEngine so demo behavior matches on both tiers.
 * [clock] is injected — never read the wall clock inside logic (TECH-NOTES 3.6 #10).
 *
 * TODO(Phase 2): replace with a trained classifier (gradient boosting over tx features)
 *   served synchronously here or behind an async consumer on `transaction.submitted` events.
 */
class FraudDetectionService(private val clock: Clock = Clock.systemDefaultZone()) {

    fun assess(request: PaymentRequest, recent: List<Transaction>): FraudAssessment {
        val reasons = mutableListOf<String>()
        var score = 0.0

        if (request.amountMinor > SINGLE_TRANSFER_LIMIT_MINOR) {
            score += WEIGHT_LARGE_AMOUNT
            reasons += "Amount exceeds the single-transfer limit"
        }

        val now = clock.millis()
        val transfersLastHour = recent.count { now - it.createdAt < WINDOW_MS }
        if (transfersLastHour >= VELOCITY_THRESHOLD) {
            score += WEIGHT_VELOCITY
            reasons += "Unusual number of transfers in the last hour ($transfersLastHour)"
        }

        val isNewPayee = recent.none { it.payeeName.equals(request.payeeName, ignoreCase = true) }
        if (isNewPayee) {
            score += WEIGHT_NEW_PAYEE
            reasons += "First transfer to this payee"
        }

        val risk = when {
            score >= HIGH_THRESHOLD -> Risk.HIGH
            score >= MEDIUM_THRESHOLD -> Risk.MEDIUM
            else -> Risk.LOW
        }
        return FraudAssessment(score = minOf(score, 1.0), risk = risk, reasons = reasons)
    }

    companion object {
        const val SINGLE_TRANSFER_LIMIT_MINOR = 200_000L
        const val WINDOW_MS = 60 * 60 * 1000L
        const val VELOCITY_THRESHOLD = 3
        const val WEIGHT_LARGE_AMOUNT = 0.55
        const val WEIGHT_VELOCITY = 0.35
        const val WEIGHT_NEW_PAYEE = 0.20
        const val HIGH_THRESHOLD = 0.55
        const val MEDIUM_THRESHOLD = 0.30
    }
}
