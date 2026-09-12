package com.example.a1_android_banking_app.fraud

import com.example.a1_android_banking_app.domain.model.FraudAssessment
import com.example.a1_android_banking_app.domain.model.FraudRisk
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransferRequest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scores a transfer against the account's recent history BEFORE submission.
 * UX-layer only — the backend engine is authoritative (ARCHITECTURE.md 2.5).
 */
interface FraudDetectionEngine {
    fun assess(request: TransferRequest, recent: List<Transaction>): FraudAssessment
}

/**
 * Deterministic rule-based v1. Weights chosen so a single strong signal (large amount,
 * or velocity + new payee) reaches HIGH and blocks pending explicit confirmation.
 * Kept in lockstep with the backend FraudDetectionService so demo behavior matches.
 *
 * [clock] is injected — never read the wall clock inside logic (TECH-NOTES 3.6 #10).
 *
 * TODO(Phase 3): replace with a quantized TFLite model (<1 MB) trained on labeled
 * transactions; keep these rules as the explainability/fallback layer.
 */
@Singleton
class RuleBasedFraudDetectionEngine @Inject constructor(
    private val clock: Clock,
) : FraudDetectionEngine {

    override fun assess(request: TransferRequest, recent: List<Transaction>): FraudAssessment {
        val reasons = mutableListOf<String>()
        var score = 0.0

        if (request.amountMinor > SINGLE_TRANSFER_LIMIT_MINOR) {
            score += WEIGHT_LARGE_AMOUNT
            reasons += "Amount exceeds the single-transfer limit"
        }

        val now = clock.millis()
        val transfersLastHour = recent.count { now - it.timestamp < WINDOW_MS }
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
            score >= HIGH_THRESHOLD -> FraudRisk.HIGH
            score >= MEDIUM_THRESHOLD -> FraudRisk.MEDIUM
            else -> FraudRisk.LOW
        }
        return FraudAssessment(score = score.coerceAtMost(1.0), risk = risk, reasons = reasons)
    }

    companion object {
        const val SINGLE_TRANSFER_LIMIT_MINOR = 200_000L // 2,000.00 major units
        const val WINDOW_MS = 60 * 60 * 1000L
        const val VELOCITY_THRESHOLD = 3
        const val WEIGHT_LARGE_AMOUNT = 0.55
        const val WEIGHT_VELOCITY = 0.35
        const val WEIGHT_NEW_PAYEE = 0.20
        const val HIGH_THRESHOLD = 0.55
        const val MEDIUM_THRESHOLD = 0.30
    }
}
