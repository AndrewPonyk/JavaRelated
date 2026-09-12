package com.example.a1_android_banking_app.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Money is `Long` minor units everywhere in this app — never Double/Float
 * (TECH-NOTES 3.6 #4). Parsing goes through BigDecimal; formatting only at the display edge.
 */

/** "12,50" / "12.5" → 1250 minor units. Returns null for blank, zero, negative or malformed input. */
fun parseAmountToMinor(input: String): Long? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val amount = runCatching { BigDecimal(normalized) }.getOrNull() ?: return null
    if (amount.signum() <= 0) return null
    return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

/** 1245075 → "12,450.75 EUR" (Locale.ROOT — stable across devices and in tests). */
fun formatMinor(minor: Long, currency: String): String =
    String.format(Locale.ROOT, "%,.2f %s", minor / 100.0, currency)
