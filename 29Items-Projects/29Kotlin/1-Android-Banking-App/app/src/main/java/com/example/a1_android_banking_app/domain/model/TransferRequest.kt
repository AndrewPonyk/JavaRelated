package com.example.a1_android_banking_app.domain.model

import java.util.UUID

data class TransferRequest(
    val fromAccountId: String,
    val payeeName: String,
    val payeeIban: String,
    /** Minor units (cents) — never a floating-point amount. */
    val amountMinor: Long,
    val currency: String,
    val reference: String = "",
    /** Makes retries safe: the backend replays the stored response for a repeated key. */
    val idempotencyKey: String = UUID.randomUUID().toString(),
)
