package com.example.a1_android_banking_app.fraud

import com.example.a1_android_banking_app.domain.model.Direction
import com.example.a1_android_banking_app.domain.model.FraudRisk
import com.example.a1_android_banking_app.domain.model.Transaction
import com.example.a1_android_banking_app.domain.model.TransactionStatus
import com.example.a1_android_banking_app.domain.model.TransferRequest
import com.example.a1_android_banking_app.util.MutableClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RuleBasedFraudDetectionEngineTest {

    private val clock = MutableClock(Instant.parse("2026-01-15T10:00:00Z").toEpochMilli())
    private val engine = RuleBasedFraudDetectionEngine(clock)

    private fun transfer(amountMinor: Long, payee: String = "Jane Doe") = TransferRequest(
        fromAccountId = "acc-1",
        payeeName = payee,
        payeeIban = "UA903052299999001111223344551",
        amountMinor = amountMinor,
        currency = "EUR",
    )

    /** A 48h-old transfer to a known payee — trips no rule (velocity window is 1h). */
    private fun oldTransfer(payee: String = "Jane Doe") = Transaction(
        id = "tx-${payee.hashCode()}",
        accountId = "acc-1",
        payeeName = payee,
        amountMinor = 100,
        currency = "EUR",
        direction = Direction.DEBIT,
        status = TransactionStatus.COMPLETED,
        reference = "",
        fraudScore = 0.0,
        timestamp = clock.instant().toEpochMilli() - 48 * 3_600_000L,
    )

    private fun recentTransfer(agoMs: Long, payee: String = "Jane Doe") = Transaction(
        id = "tx-recent-$agoMs",
        accountId = "acc-1",
        payeeName = payee,
        amountMinor = 100,
        currency = "EUR",
        direction = Direction.DEBIT,
        status = TransactionStatus.COMPLETED,
        reference = "",
        fraudScore = 0.0,
        timestamp = clock.instant().toEpochMilli() - agoMs,
    )

    @Test
    fun `large single transfer is flagged high risk`() {
        val assessment = engine.assess(transfer(amountMinor = 500_000), recent = listOf(oldTransfer()))

        assertEquals(FraudRisk.HIGH, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("limit", ignoreCase = true) })
    }

    @Test
    fun `amount exactly at the limit is low risk`() {
        val assessment = engine.assess(
            transfer(amountMinor = RuleBasedFraudDetectionEngine.SINGLE_TRANSFER_LIMIT_MINOR),
            recent = listOf(oldTransfer()),
        )
        assertEquals(FraudRisk.LOW, assessment.risk)
    }

    @Test
    fun `small transfer to known payee is low risk`() {
        val assessment = engine.assess(transfer(amountMinor = 2_500), recent = listOf(oldTransfer()))

        assertEquals(FraudRisk.LOW, assessment.risk)
        assertTrue(assessment.reasons.isEmpty())
    }

    @Test
    fun `unknown payee alone is not blocked`() {
        val assessment = engine.assess(transfer(amountMinor = 500, payee = "New Shop"), recent = listOf(oldTransfer()))

        assertEquals(FraudRisk.LOW, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("payee", ignoreCase = true) })
    }

    @Test
    fun `velocity alone is medium risk`() {
        val recent = listOf(
            recentTransfer(10 * 60_000L),
            recentTransfer(20 * 60_000L),
            recentTransfer(30 * 60_000L),
        )
        val assessment = engine.assess(transfer(amountMinor = 1_000), recent = recent)

        assertEquals(FraudRisk.MEDIUM, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("last hour") })
    }

    @Test
    fun `velocity plus new payee is high risk`() {
        val recent = listOf(
            recentTransfer(10 * 60_000L),
            recentTransfer(20 * 60_000L),
            recentTransfer(30 * 60_000L),
        )
        val assessment = engine.assess(transfer(amountMinor = 1_000, payee = "Someone Else"), recent = recent)

        assertEquals(FraudRisk.HIGH, assessment.risk)
    }

    @Test
    fun `transfer exactly at the velocity window edge is not counted`() {
        val assessment = engine.assess(
            transfer(amountMinor = 1_000),
            recent = listOf(recentTransfer(RuleBasedFraudDetectionEngine.WINDOW_MS)),
        )
        assertEquals(FraudRisk.LOW, assessment.risk)
    }

    @Test
    fun `score is clamped to 1_0`() {
        val recent = listOf(
            recentTransfer(10 * 60_000L, payee = "Other"),
            recentTransfer(20 * 60_000L, payee = "Other"),
            recentTransfer(30 * 60_000L, payee = "Other"),
        )
        val assessment = engine.assess(transfer(amountMinor = 500_000, payee = "New Payee"), recent = recent)

        assertEquals(1.0, assessment.score, 0.0)
        assertEquals(FraudRisk.HIGH, assessment.risk)
    }
}
