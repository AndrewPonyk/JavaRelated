package com.example.a1_android_banking_app.domain.model

data class Account(
    val id: String,
    val name: String,
    val iban: String,
    val balanceMinor: Long,
    val currency: String,
    val type: String,
) {
    /** Money is stored as Long minor units everywhere; format only at the display edge. */
    val formattedBalance: String
        get() = formatMinor(balanceMinor, currency)
}
