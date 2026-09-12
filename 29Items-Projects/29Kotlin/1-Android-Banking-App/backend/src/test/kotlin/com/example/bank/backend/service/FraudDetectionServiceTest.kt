package com.example.bank.backend.service

import com.example.bank.backend.model.PaymentRequest
import com.example.bank.backend.model.Risk
import com.example.bank.backend.model.Transaction
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FraudDetectionServiceTest {

    private val nowMs = Instant.parse("2026-01-15T10:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(nowMs), ZoneOffset.UTC)
    private val service = FraudDetectionService(clock)

    private fun payment(amountMinor: Long, payeeName: String = "Jane Doe") = PaymentRequest(
        fromAccountId = "acc-1",
        payeeName = payeeName,
        payeeIban = "UA213223130000026007233566001",
        amountMinor = amountMinor,
        currency = "EUR",
    )

    private fun history(vararg agoMsList: Long, payeeName: String = "Jane Doe") =
        agoMsList.map { agoMs ->
            Transaction(
                id = "tx-$agoMs",
                accountId = "acc-1",
                payeeName = payeeName,
                amountMinor = 100,
                currency = "EUR",
                direction = "DEBIT",
                status = "COMPLETED",
                reference = "",
                fraudScore = 0.0,
                createdAt = nowMs - agoMs,
            )
        }

    @Test
    fun `no rule trips on a small transfer to a known payee`() {
        val assessment = service.assess(
            payment(amountMinor = 2_500),
            recent = history(FraudDetectionService.WINDOW_MS * 24),
        )
        assertEquals(Risk.LOW, assessment.risk)
        assertTrue(assessment.reasons.isEmpty())
    }

    @Test
    fun `amount exactly at the single-transfer limit is not flagged`() {
        val assessment = service.assess(
            payment(amountMinor = FraudDetectionService.SINGLE_TRANSFER_LIMIT_MINOR),
            recent = history(FraudDetectionService.WINDOW_MS * 24),
        )
        assertEquals(Risk.LOW, assessment.risk)
        assertTrue(assessment.reasons.none { it.contains("limit") })
    }

    @Test
    fun `oversized amount alone is high risk`() {
        val assessment = service.assess(
            payment(amountMinor = FraudDetectionService.SINGLE_TRANSFER_LIMIT_MINOR + 1),
            recent = history(FraudDetectionService.WINDOW_MS * 24),
        )
        assertEquals(Risk.HIGH, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("limit") })
    }

    @Test
    fun `velocity alone is medium risk`() {
        val assessment = service.assess(
            payment(amountMinor = 1_000),
            // Three prior transfers inside the window, to the same payee.
            recent = history(10 * 60_000L, 20 * 60_000L, 30 * 60_000L),
        )
        assertEquals(Risk.MEDIUM, assessment.risk)
        assertTrue(assessment.reasons.any { it.contains("last hour") })
    }

    @Test
    fun `velocity plus new payee escalates to high risk`() {
        val assessment = service.assess(
            payment(amountMinor = 1_000, payeeName = "Brand New Payee"),
            recent = history(10 * 60_000L, 20 * 60_000L, 30 * 60_000L, payeeName = "Jane Doe"),
        )
        assertEquals(Risk.HIGH, assessment.risk)
    }

    @Test
    fun `transfer exactly at the velocity window edge is not counted`() {
        val assessment = service.assess(
            payment(amountMinor = 1_000),
            // Exactly WINDOW_MS old: `now - createdAt < WINDOW_MS` is false → not counted.
            recent = history(FraudDetectionService.WINDOW_MS),
        )
        assertEquals(Risk.LOW, assessment.risk)
        assertTrue(assessment.reasons.none { it.contains("last hour") })
    }

    @Test
    fun `transfers outside the window do not trip velocity`() {
        val assessment = service.assess(
            payment(amountMinor = 1_000),
            recent = history(FraudDetectionService.WINDOW_MS * 2, FraudDetectionService.WINDOW_MS * 3),
        )
        assertEquals(Risk.LOW, assessment.risk)
    }

    @Test
    fun `score is clamped to 1_0`() {
        // Every rule trips: oversized + velocity + new payee = 0.55 + 0.35 + 0.20 = 1.10 → clamp 1.0.
        val assessment = service.assess(
            payment(amountMinor = 500_000, payeeName = "New Payee"),
            recent = history(10 * 60_000L, 20 * 60_000L, 30 * 60_000L, payeeName = "Other"),
        )
        assertEquals(1.0, assessment.score)
        assertEquals(Risk.HIGH, assessment.risk)
    }
}
