package com.example.a1_android_banking_app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class MoneyTest {

    // ---- parseAmountToMinor (TECH-NOTES 3.6 #4 — BigDecimal, never Double) ----

    @Test
    fun `parses decimal comma and decimal point identically`() {
        assertEquals(1250L, parseAmountToMinor("12,50"))
        assertEquals(1250L, parseAmountToMinor("12.5"))
        assertEquals(1250L, parseAmountToMinor("12.50"))
    }

    @Test
    fun `parses plain major units`() {
        assertEquals(100_000L, parseAmountToMinor("1000"))
    }

    @Test
    fun `rounds half-up to whole minor units`() {
        assertEquals(1300L, parseAmountToMinor("12.999"))
        assertEquals(1255L, parseAmountToMinor("12.554"))
    }

    @Test
    fun `rejects blank zero negative and malformed input`() {
        assertNull(parseAmountToMinor(""))
        assertNull(parseAmountToMinor("   "))
        assertNull(parseAmountToMinor("0"))
        assertNull(parseAmountToMinor("0.00"))
        assertNull(parseAmountToMinor("-3"))
        assertNull(parseAmountToMinor("abc"))
        assertNull(parseAmountToMinor("1.2.3"))
        assertNull(parseAmountToMinor("12,5,0"))
    }

    @Test
    fun `exponent notation is parseable BigDecimal input`() {
        assertEquals(100_000L, parseAmountToMinor("1e3"))
    }

    // ---- formatMinor ----

    @Test
    fun `formats minor units with grouping and currency`() {
        assertEquals("12,450.75 EUR", formatMinor(1_245_075, "EUR"))
        assertEquals("0.05 EUR", formatMinor(5, "EUR"))
        assertEquals("0.00 USD", formatMinor(0, "USD"))
    }

    @Test
    fun `account formattedBalance uses minor units`() {
        val account = Account(
            id = "acc-1", name = "Main", iban = "UA90", balanceMinor = 1_245_075,
            currency = "EUR", type = "CHECKING",
        )
        assertEquals("12,450.75 EUR", account.formattedBalance)
    }

    // ---- Transaction display formatting ----

    private fun transaction(direction: Direction) = Transaction(
        id = "tx-1", accountId = "acc-1", payeeName = "Jane Doe",
        amountMinor = 1_250, currency = "EUR", direction = direction,
        status = TransactionStatus.COMPLETED, reference = "", fraudScore = 0.0,
        timestamp = Instant.parse("2026-01-15T10:30:00Z").toEpochMilli(),
    )

    @Test
    fun `debit amounts are minus-prefixed and credits plus-prefixed`() {
        assertEquals("-12.50 EUR", transaction(Direction.DEBIT).formattedAmount)
        assertEquals("+12.50 EUR", transaction(Direction.CREDIT).formattedAmount)
    }

    @Test
    fun `statement dates format deterministically for a fixed zone`() {
        assertEquals("15 Jan 2026, 10:30", transaction(Direction.DEBIT).formattedDate(ZoneOffset.UTC))
    }

    @Test
    fun `parse then format round-trips two-decimal input`() {
        val minor = parseAmountToMinor("12.34")
        assertEquals(1234L, minor)
        assertEquals("12.34 EUR", formatMinor(minor!!, "EUR"))
    }

    @Test
    fun `sanity - double arithmetic would fail the third decimal case but BigDecimal does not`() {
        assertTrue(parseAmountToMinor("0.1") == 10L)
        assertFalse(parseAmountToMinor("0.145") == 14L) // HALF_UP → 15
        assertEquals(15L, parseAmountToMinor("0.145"))
    }
}
