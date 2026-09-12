package com.example.a1_android_banking_app.domain.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Direction { DEBIT, CREDIT }

enum class TransactionStatus { PENDING, COMPLETED, DECLINED }

data class Transaction(
    val id: String,
    val accountId: String,
    val payeeName: String,
    val amountMinor: Long,
    val currency: String,
    val direction: Direction,
    val status: TransactionStatus,
    val reference: String,
    val fraudScore: Double,
    val timestamp: Long,
) {
    /** "-12.50 EUR" for DEBIT, "+12.50 EUR" for CREDIT. */
    val formattedAmount: String
        get() = (if (direction == Direction.DEBIT) "-" else "+") + formatMinor(amountMinor, currency)

    /** Statement row date. Zone is a parameter so tests stay deterministic. */
    fun formattedDate(zone: ZoneId = ZoneId.systemDefault()): String =
        DATE_FORMATTER.withZone(zone).format(Instant.ofEpochMilli(timestamp))

    private companion object {
        val DATE_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ROOT)
    }
}
